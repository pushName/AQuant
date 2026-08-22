package com.brotherc.aquant.llm.service;

import com.brotherc.aquant.llm.entity.*;
import com.brotherc.aquant.llm.model.vo.*;
import com.brotherc.aquant.llm.repository.*;
import com.brotherc.aquant.common.utils.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

/** 分析作业编排、持久化、Python 调度和浏览器事件推送服务。 */
@Service
public class AnalysisJobService {
    private final AnalysisJobRepository jobRepository;
    private final AnalysisJobEventRepository eventRepository;
    private final PromptTemplateService promptTemplateService;
    private final PythonAnalysisClient pythonClient;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private final long pollMillis;
    private final Map<String, Set<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public AnalysisJobService(AnalysisJobRepository jobRepository,
                              AnalysisJobEventRepository eventRepository,
                              PromptTemplateService promptTemplateService,
                              PythonAnalysisClient pythonClient,
                              ObjectMapper objectMapper,
                              ExecutorService analysisExecutor,
                              @Value("${analysis.python.poll-millis:1000}") long pollMillis) {
        this.jobRepository = jobRepository;
        this.eventRepository = eventRepository;
        this.promptTemplateService = promptTemplateService;
        this.pythonClient = pythonClient;
        this.objectMapper = objectMapper;
        this.executor = analysisExecutor;
        this.pollMillis = Math.max(200, pollMillis);
    }

    /** 应用重启后重新接管未完成作业。 */
    @PostConstruct
    public void recoverJobs() {
        promptTemplateService.ensureDefaults();
        for (AnalysisJob job : jobRepository.findByStatusIn(Arrays.asList(
                AnalysisJobStatus.QUEUED, AnalysisJobStatus.RUNNING, AnalysisJobStatus.CANCELLING))) {
            executor.submit(() -> runJob(job.getId()));
        }
    }

    /** 创建作业并固定当前已发布提示词快照。 */
    public AnalysisJobResponse create(AnalysisJobCreateRequest request) {
        if (request.getTickers().size() > 500) throw new IllegalArgumentException("单次最多分析 500 只股票");
        AnalysisJob job = new AnalysisJob();
        job.setStatus(AnalysisJobStatus.QUEUED);
        job.setStage(AnalysisStage.DATA_PREPARING);
        job.setAnalysisDate(request.getDate());
        job.setTotalCount(request.getTickers().size());
        job.setCompletedCount(0); job.setFailedCount(0); job.setProgress(0);
        job.setCreatedBy(UserContext.getCurrentUserId());
        job.setCreatedUsername(UserContext.getCurrentUsername());
        job.setTickersJson(toJson(request.getTickers()));
        job.setConfigJson(toJson(request));
        job = jobRepository.save(job);

        Map<String, Object> snapshot = promptTemplateService.snapshot(job.getId());
        job.setPromptSnapshotHash(String.valueOf(snapshot.get("hash")));
        jobRepository.save(job);
        addEvent(job, "JOB_STATUS", AnalysisStage.DATA_PREPARING, null, null, "QUEUED", "作业已排队", null);
        String jobId = job.getId();
        executor.submit(() -> runJob(jobId));
        return toResponse(job);
    }

    /** 分页查询作业。 */
    public Page<AnalysisJobResponse> list(int page, int size) {
        int safeSize = Math.max(1, Math.min(100, size));
        Page<AnalysisJob> result = jobRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(Math.max(0, page), safeSize, Sort.by(Sort.Direction.DESC, "createdAt")));
        return result.map(this::toResponse);
    }

    public AnalysisJobResponse get(String jobId) {
        return toResponse(findJob(jobId));
    }

    public String result(String jobId) {
        return Optional.ofNullable(findJob(jobId).getResultJson()).orElse("{}");
    }

    /** 请求安全取消，不强杀正在执行的上游 HTTP 请求。 */
    public AnalysisJobResponse cancel(String jobId) {
        AnalysisJob job = findJob(jobId);
        if (isTerminal(job.getStatus())) return toResponse(job);
        job.setCancelRequested(true);
        job.setStatus(AnalysisJobStatus.CANCELLING);
        jobRepository.save(job);
        addEvent(job, "JOB_STATUS", job.getStage(), null, null, "CANCELLING", "已请求取消，等待当前节点完成", null);
        if (job.getPythonJobId() != null) {
            try { pythonClient.cancel(job.getPythonJobId()); }
            catch (Exception exception) { addEvent(job, "WARNING", job.getStage(), null, null, "WARNING", "通知 Python 取消失败", null); }
        }
        return toResponse(jobRepository.findById(jobId).orElse(job));
    }

    /** 失败或取消作业创建新的尝试，原作业记录保持不变。 */
    public AnalysisJobResponse retry(String jobId) {
        AnalysisJob old = findJob(jobId);
        if (!isTerminal(old.getStatus()) || old.getStatus() == AnalysisJobStatus.SUCCEEDED) {
            throw new IllegalStateException("只有失败或取消的作业才能重试");
        }
        AnalysisJobCreateRequest request = new AnalysisJobCreateRequest();
        request.setDate(old.getAnalysisDate());
        request.setTickers(fromJson(old.getTickersJson(), new TypeReference<List<String>>() {}));
        Map<String, Object> config = fromJson(old.getConfigJson(), new TypeReference<Map<String, Object>>() {});
        request.setSkipKronos(Boolean.TRUE.equals(config.get("skipKronos")));
        request.setStreaming(Boolean.TRUE.equals(config.get("streaming")));
        return create(request);
    }

    /** 返回指定序号之后的事件。 */
    public List<AnalysisJobEventResponse> events(String jobId, long afterSeq) {
        findJob(jobId);
        List<AnalysisJobEvent> events = eventRepository.findByJobIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(jobId, afterSeq);
        List<AnalysisJobEventResponse> result = new ArrayList<>();
        for (AnalysisJobEvent event : events) result.add(toEventResponse(event));
        return result;
    }

    /** 创建浏览器 SSE 连接，并先发送断线期间的历史事件。 */
    public SseEmitter openStream(String jobId, long afterSeq) {
        findJob(jobId);
        SseEmitter emitter = new SseEmitter(0L);
        Set<SseEmitter> set = emitters.computeIfAbsent(jobId, ignored -> ConcurrentHashMap.newKeySet());
        set.add(emitter);
        emitter.onCompletion(() -> removeEmitter(jobId, emitter));
        emitter.onTimeout(() -> removeEmitter(jobId, emitter));
        emitter.onError(ignored -> removeEmitter(jobId, emitter));
        try {
            for (AnalysisJobEventResponse event : events(jobId, afterSeq)) {
                emitter.send(SseEmitter.event().id(String.valueOf(event.getSeq())).name(event.getType())
                        .data(event, MediaType.APPLICATION_JSON));
            }
        } catch (Exception exception) { removeEmitter(jobId, emitter); }
        return emitter;
    }

    private void runJob(String jobId) {
        AnalysisJob job = findJob(jobId);
        if (isTerminal(job.getStatus())) return;
        try {
            job.setStatus(AnalysisJobStatus.RUNNING);
            job.setStartedAt(job.getStartedAt() == null ? LocalDateTime.now() : job.getStartedAt());
            jobRepository.save(job);
            addEvent(job, "JOB_STATUS", AnalysisStage.DATA_PREPARING, null, null, "RUNNING", "开始调用 Python 分析服务", null);
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("jobId", job.getId());
            request.put("date", job.getAnalysisDate().toString());
            request.put("tickers", fromJson(job.getTickersJson(), new TypeReference<List<String>>() {}));
            Map<String, Object> config = fromJson(job.getConfigJson(), new TypeReference<Map<String, Object>>() {});
            request.put("skipKronos", config.getOrDefault("skipKronos", false));
            request.put("streaming", config.getOrDefault("streaming", false));
            request.put("promptSnapshotHash", job.getPromptSnapshotHash());
            request.put("promptSnapshots", promptTemplateService.snapshot(job.getId()).get("templates"));
            String pythonJobId = job.getPythonJobId();
            if (pythonJobId == null) {
                pythonJobId = pythonClient.start(request);
                job.setPythonJobId(pythonJobId);
                jobRepository.save(job);
            }
            long pythonSequence = 0L;
            while (true) {
                AnalysisJob current = findJob(jobId);
                if (Boolean.TRUE.equals(current.getCancelRequested())) {
                    try { pythonClient.cancel(pythonJobId); } catch (Exception ignored) { }
                }
                JsonNode events = pythonClient.events(pythonJobId, pythonSequence);
                JsonNode array = events.path("events");
                if (!array.isArray()) array = events.path("data").path("events");
                if (array.isArray()) {
                    for (JsonNode node : array) {
                        long sourceSeq = node.path("seq").asLong(node.path("sequenceNo").asLong(pythonSequence + 1));
                        pythonSequence = Math.max(pythonSequence, sourceSeq);
                        applyPythonEvent(current, node, sourceSeq);
                    }
                }
                JsonNode state = pythonClient.status(pythonJobId);
                String status = state.path("status").asText(state.path("data").path("status").asText("RUNNING"));
                if ("SUCCEEDED".equals(status) || "FAILED".equals(status) || "CANCELLED".equals(status)) {
                    finishFromPython(current, state, status);
                    return;
                }
                Thread.sleep(pollMillis);
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            markFailed(jobId, "分析工作线程被中断");
        } catch (Exception exception) {
            markFailed(jobId, safeMessage(exception));
        }
    }

    private void applyPythonEvent(AnalysisJob job, JsonNode node, long seq) {
        String stageValue = node.path("stage").asText(null);
        AnalysisStage stage = parseStage(stageValue, job.getStage());
        String status = node.path("status").asText("RUNNING");
        job.setStage(stage);
        if (node.has("completed")) job.setCompletedCount(node.path("completed").asInt(job.getCompletedCount()));
        if (node.has("total")) job.setTotalCount(node.path("total").asInt(job.getTotalCount()));
        if (job.getTotalCount() != null && job.getTotalCount() > 0) {
            job.setProgress(Math.min(100, Math.max(0, job.getCompletedCount() * 100 / job.getTotalCount())));
        }
        jobRepository.save(job);
        addEvent(job, node.path("type").asText("PROGRESS"), stage,
                node.path("role").asText(null), node.path("ticker").asText(null), status,
                node.path("message").asText("Python 分析进度更新"), node.toString(), seq);
    }

    private void finishFromPython(AnalysisJob job, JsonNode state, String status) {
        job = findJob(job.getId());
        if ("CANCELLED".equals(status) || Boolean.TRUE.equals(job.getCancelRequested())) {
            job.setStatus(AnalysisJobStatus.CANCELLED);
            addEvent(job, "JOB_STATUS", job.getStage(), null, null, "CANCELLED", "作业已取消", null);
        } else if ("SUCCEEDED".equals(status)) {
            job.setStatus(AnalysisJobStatus.SUCCEEDED);
            job.setStage(AnalysisStage.COMPLETED);
            job.setProgress(100);
            JsonNode result = state.path("result").isMissingNode() ? state.path("data").path("result") : state.path("result");
            job.setResultJson(result.toString());
            addEvent(job, "JOB_STATUS", AnalysisStage.COMPLETED, null, null, "SUCCEEDED", "作业完成", null);
        } else {
            job.setStatus(AnalysisJobStatus.FAILED);
            job.setErrorMessage(state.path("error").asText(state.path("data").path("error").asText("Python 作业失败")));
            addEvent(job, "JOB_STATUS", job.getStage(), null, null, "FAILED", "作业失败", null);
        }
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
    }

    private void markFailed(String jobId, String message) {
        AnalysisJob job = findJob(jobId);
        if (isTerminal(job.getStatus())) return;
        job.setStatus(AnalysisJobStatus.FAILED);
        job.setErrorMessage(message);
        job.setFinishedAt(LocalDateTime.now());
        jobRepository.save(job);
        addEvent(job, "JOB_STATUS", job.getStage(), null, null, "FAILED", message, null);
    }

    private void addEvent(AnalysisJob job, String type, AnalysisStage stage, String role, String ticker,
                          String status, String message, String payload) {
        addEvent(job, type, stage, role, ticker, status, message, payload, null);
    }

    private synchronized void addEvent(AnalysisJob job, String type, AnalysisStage stage, String role, String ticker,
                                       String status, String message, String payload, Long sourceSeq) {
        if (sourceSeq != null && eventRepository.findFirstByJobIdAndSourceSeq(job.getId(), sourceSeq).isPresent()) return;
        long seq = eventRepository.findTopByJobIdOrderBySequenceNoDesc(job.getId())
                .map(event -> event.getSequenceNo() + 1).orElse(1L);
        AnalysisJobEvent event = new AnalysisJobEvent();
        event.setJobId(job.getId()); event.setSequenceNo(seq); event.setType(type);
        event.setSourceSeq(sourceSeq);
        event.setStage(stage == null ? null : stage.name()); event.setRole(role); event.setTicker(ticker);
        event.setStatus(status); event.setCompleted(job.getCompletedCount()); event.setTotal(job.getTotalCount());
        event.setMessage(message); event.setPayloadJson(payload); eventRepository.save(event);
        AnalysisJobEventResponse response = toEventResponse(event);
        for (SseEmitter emitter : emitters.getOrDefault(job.getId(), Collections.emptySet())) {
            try { emitter.send(SseEmitter.event().id(String.valueOf(seq)).name(type).data(response, MediaType.APPLICATION_JSON)); }
            catch (Exception exception) { removeEmitter(job.getId(), emitter); }
        }
    }

    private void removeEmitter(String jobId, SseEmitter emitter) {
        Set<SseEmitter> set = emitters.get(jobId);
        if (set != null) { set.remove(emitter); if (set.isEmpty()) emitters.remove(jobId); }
    }

    private AnalysisJob findJob(String jobId) {
        return jobRepository.findById(jobId).orElseThrow(() -> new IllegalArgumentException("分析作业不存在: " + jobId));
    }

    private boolean isTerminal(AnalysisJobStatus status) {
        return status == AnalysisJobStatus.SUCCEEDED || status == AnalysisJobStatus.FAILED || status == AnalysisJobStatus.CANCELLED;
    }

    private AnalysisStage parseStage(String value, AnalysisStage fallback) {
        if (value == null) return fallback;
        try { return AnalysisStage.valueOf(value); } catch (Exception ignored) { return fallback; }
    }

    private AnalysisJobResponse toResponse(AnalysisJob job) {
        AnalysisJobResponse response = new AnalysisJobResponse();
        response.setId(job.getId()); response.setStatus(job.getStatus().name()); response.setStage(job.getStage().name());
        response.setDate(job.getAnalysisDate()); response.setTickers(fromJson(job.getTickersJson(), new TypeReference<List<String>>() {}));
        response.setTotal(job.getTotalCount()); response.setCompleted(job.getCompletedCount()); response.setFailed(job.getFailedCount());
        response.setProgress(job.getProgress()); response.setPythonJobId(job.getPythonJobId()); response.setErrorMessage(job.getErrorMessage());
        response.setCancelRequested(job.getCancelRequested()); response.setCreatedAt(job.getCreatedAt()); response.setUpdatedAt(job.getUpdatedAt());
        response.setStartedAt(job.getStartedAt()); response.setFinishedAt(job.getFinishedAt());
        return response;
    }

    private AnalysisJobEventResponse toEventResponse(AnalysisJobEvent event) {
        AnalysisJobEventResponse response = new AnalysisJobEventResponse();
        response.setSeq(event.getSequenceNo()); response.setType(event.getType()); response.setStage(event.getStage());
        response.setRole(event.getRole()); response.setTicker(event.getTicker()); response.setStatus(event.getStatus());
        response.setCompleted(event.getCompleted()); response.setTotal(event.getTotal()); response.setMessage(event.getMessage());
        response.setPayload(event.getPayloadJson()); response.setTimestamp(event.getEventAt());
        return response;
    }

    private String toJson(Object value) { try { return objectMapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException("分析配置序列化失败", e); } }
    private <T> T fromJson(String value, TypeReference<T> type) { try { return objectMapper.readValue(value == null ? "{}" : value, type); } catch (Exception e) { throw new IllegalStateException("分析配置解析失败", e); } }
    private String safeMessage(Exception e) { String message = e.getMessage(); return message == null ? e.getClass().getSimpleName() : message.substring(0, Math.min(1000, message.length())); }
}
