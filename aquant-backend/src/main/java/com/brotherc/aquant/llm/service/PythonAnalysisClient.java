package com.brotherc.aquant.llm.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Python 分析服务 HTTP 客户端。Java 不直接依赖 Python 模块。 */
@Component
public class PythonAnalysisClient {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public PythonAnalysisClient(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${analysis.python.base-url:http://127.0.0.1:8000}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl.replaceAll("/$", "");
    }

    /** 创建 Python 作业并返回其作业 ID。 */
    public String start(Map<String, Object> request) {
        JsonNode body = post("/v1/analysis/jobs", request);
        JsonNode data = body.path("data");
        if (data.isMissingNode()) data = body;
        // 新版 Python 服务返回 jobId；兼容旧版状态对象中的 job_id/id。
        String id = data.path("jobId").asText(null);
        if (id == null || id.isEmpty()) id = data.path("job_id").asText(null);
        if (id == null || id.isEmpty()) id = data.path("id").asText(null);
        if (id == null || id.isEmpty()) {
            throw new IllegalStateException("Python 分析服务未返回作业 ID");
        }
        return id;
    }

    /** 查询 Python 作业事件。 */
    public JsonNode events(String jobId, long afterSeq) {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/v1/analysis/jobs/" + jobId + "/events?afterSeq=" + afterSeq,
                String.class);
        return read(response.getBody());
    }

    /** 查询 Python 作业当前状态和结果。 */
    public JsonNode status(String jobId) {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "/v1/analysis/jobs/" + jobId, String.class);
        return read(response.getBody());
    }

    /**
     * 获取 Python 服务从 TradingAgents 源码导出的角色提示词目录。
     * 该接口只返回提示词基线，不会启动分析作业或调用大模型。
     */
    public JsonNode promptCatalog() {
        try {
            ResponseEntity<String> response = restTemplate.getForEntity(
                    baseUrl + "/v1/analysis/prompts/catalog", String.class);
            JsonNode body = read(response.getBody());
            JsonNode data = body.path("data");
            if (!data.isArray()) {
                throw new IllegalStateException("Python 提示词目录返回格式无效");
            }
            return data;
        } catch (Exception exception) {
            throw new IllegalStateException("获取 Python 源码提示词失败: " + exception.getMessage(), exception);
        }
    }

    /** 请求 Python 在安全节点取消作业。 */
    public void cancel(String jobId) {
        post("/v1/analysis/jobs/" + jobId + "/cancel", Collections.emptyMap());
    }

    /** 幂等删除 Python 终态作业；Python 404 表示作业已经不存在。 */
    public void delete(String jobId) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    baseUrl + "/v1/analysis/jobs/" + jobId,
                    HttpMethod.DELETE,
                    HttpEntity.EMPTY,
                    String.class);
            JsonNode body = read(response.getBody());
            if (!body.path("success").asBoolean(false)) {
                throw new IllegalStateException("Python 分析服务拒绝删除作业");
            }
        } catch (HttpStatusCodeException exception) {
            if (exception.getStatusCode().value() == 404) return;
            throw new IllegalStateException(
                    "Python 分析服务删除作业失败（HTTP " + exception.getStatusCode().value() + ")", exception);
        } catch (Exception exception) {
            if (exception instanceof IllegalStateException stateException) throw stateException;
            throw new IllegalStateException("Python 分析服务删除作业失败: " + exception.getMessage(), exception);
        }
    }

    private JsonNode post(String path, Object request) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Object> entity = new HttpEntity<>(request, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(baseUrl + path, entity, String.class);
            return read(response.getBody());
        } catch (Exception exception) {
            throw new IllegalStateException("Python 分析服务调用失败: " + exception.getMessage(), exception);
        }
    }

    private JsonNode read(String body) {
        try {
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (Exception exception) {
            throw new IllegalStateException("Python 分析服务返回格式无效", exception);
        }
    }
}
