package com.brotherc.aquant.llm.service;

import com.brotherc.aquant.llm.entity.*;
import com.brotherc.aquant.llm.model.vo.PromptDraftRequest;
import com.brotherc.aquant.llm.model.vo.PromptTemplateResponse;
import com.brotherc.aquant.llm.model.vo.PromptVersionResponse;
import com.brotherc.aquant.llm.repository.*;
import com.brotherc.aquant.common.utils.UserContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** 提示词模板、版本发布和作业快照服务。 */
@Service
public class PromptTemplateService {
    public static final List<String> ROLE_KEYS = Collections.unmodifiableList(Arrays.asList(
            "market", "social", "news", "fundamentals", "policy", "hot_money", "lockup",
            "quality_gate", "bull", "bear", "research_manager", "trader",
            "risk_aggressive", "risk_neutral", "risk_conservative", "portfolio_manager"));
    private static final Pattern VARIABLE = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_]+)\\s*}}" );
    private static final Set<String> DEFAULT_VARIABLES = new LinkedHashSet<>(Arrays.asList(
            "ticker", "date", "current_date", "reports", "messages", "tool_names", "debate_round"));

    private final PromptTemplateRepository templateRepository;
    private final PromptVersionRepository versionRepository;
    private final AnalysisJobPromptSnapshotRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public PromptTemplateService(PromptTemplateRepository templateRepository,
                                 PromptVersionRepository versionRepository,
                                 AnalysisJobPromptSnapshotRepository snapshotRepository,
                                 ObjectMapper objectMapper) {
        this.templateRepository = templateRepository;
        this.versionRepository = versionRepository;
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    /** 确保首次启动时每个角色都有可编辑的默认模板。 */
    @Transactional
    public void ensureDefaults() {
        for (String role : ROLE_KEYS) {
            // 模板与默认 v1 版本均按唯一键原子写入（INSERT IGNORE），
            // 避免并发调用（页面加载 + 导入 + 建任务快照）时先查后插撞唯一键。
            templateRepository.insertRoleTemplateIfAbsent(role, "ROLE", "TradingAgents 角色提示词");
            // 加锁重查：可重复读事务的普通快照可能看不到并发方刚提交的行，
            // FOR UPDATE 始终读到最新已提交状态，拿到真实 template id。
            PromptTemplate template = templateRepository.findByRoleKeyAndTemplateTypeForUpdate(role, "ROLE")
                    .orElseThrow(() -> new IllegalStateException("提示词模板初始化失败: " + role));
            String content = defaultContent(role);
            versionRepository.insertDefaultIfAbsent(template.getId(), content,
                    toJson(new ArrayList<>(DEFAULT_VARIABLES)), hash(content));
            if (template.getPublishedVersion() == null) {
                template.setPublishedVersion(1);
                templateRepository.save(template);
            }
        }
    }

    @Transactional
    public List<PromptTemplateResponse> listTemplates() {
        ensureDefaults();
        return templateRepository.findAll().stream().map(this::toTemplateResponse).collect(Collectors.toList());
    }

    public List<PromptVersionResponse> listVersions(String roleKey, String templateType) {
        PromptTemplate template = getTemplate(roleKey, templateType);
        return versionRepository.findByTemplateIdOrderByVersionNoDesc(template.getId()).stream()
                .map(version -> toVersionResponse(template, version)).collect(Collectors.toList());
    }

    public PromptVersionResponse getVersion(String roleKey, String templateType, Integer versionNo) {
        PromptTemplate template = getTemplate(roleKey, templateType);
        PromptVersion version = versionRepository.findByTemplateIdAndVersionNo(template.getId(), versionNo)
                .orElseThrow(() -> new IllegalArgumentException("提示词版本不存在"));
        return toVersionResponse(template, version);
    }

    /** 创建新草稿，不修改当前发布版本。 */
    @Transactional
    public PromptVersionResponse saveDraft(String roleKey, String templateType, PromptDraftRequest request) {
        validateRole(roleKey);
        validateContent(request.getContent(), request.getVariables());
        PromptTemplate template = templateRepository.findByRoleKeyAndTemplateType(roleKey, templateType)
                .orElseGet(() -> createTemplate(roleKey, templateType));
        int next = versionRepository.findByTemplateIdOrderByVersionNoDesc(template.getId()).stream()
                .mapToInt(v -> v.getVersionNo() == null ? 0 : v.getVersionNo()).max().orElse(0) + 1;
        PromptVersion version = new PromptVersion();
        version.setTemplateId(template.getId());
        version.setVersionNo(next);
        version.setContent(request.getContent());
        version.setVariablesJson(toJson(request.getVariables() == null ? extractVariables(request.getContent()) : request.getVariables()));
        version.setContentHash(hash(request.getContent()));
        version.setStatus(PromptVersionStatus.DRAFT);
        version.setCreatedBy(UserContext.getCurrentUserId());
        return toVersionResponse(template, versionRepository.save(version));
    }

    /** 更新指定草稿版本，已发布或已归档版本不得被覆盖。 */
    @Transactional
    public PromptVersionResponse updateDraft(String roleKey, String templateType, Integer versionNo,
                                             PromptDraftRequest request) {
        validateRole(roleKey);
        validateContent(request.getContent(), request.getVariables());
        PromptTemplate template = getTemplate(roleKey, templateType);
        PromptVersion version = versionRepository.findByTemplateIdAndVersionNo(template.getId(), versionNo)
                .orElseThrow(() -> new IllegalArgumentException("提示词版本不存在"));
        if (version.getStatus() != PromptVersionStatus.DRAFT) {
            throw new IllegalStateException("只有草稿版本可以修改");
        }
        version.setContent(request.getContent());
        version.setVariablesJson(toJson(request.getVariables() == null ? extractVariables(request.getContent()) : request.getVariables()));
        version.setContentHash(hash(request.getContent()));
        return toVersionResponse(template, versionRepository.save(version));
    }

    /** 发布指定版本，并将旧发布版本归档。 */
    @Transactional
    public PromptVersionResponse publish(String roleKey, String templateType, Integer versionNo) {
        PromptTemplate template = getTemplate(roleKey, templateType);
        PromptVersion target = versionRepository.findByTemplateIdAndVersionNo(template.getId(), versionNo)
                .orElseThrow(() -> new IllegalArgumentException("提示词版本不存在"));
        List<PromptVersion> versions = versionRepository.findByTemplateIdOrderByVersionNoDesc(template.getId());
        for (PromptVersion version : versions) {
            if (version.getStatus() == PromptVersionStatus.PUBLISHED) version.setStatus(PromptVersionStatus.ARCHIVED);
        }
        target.setStatus(PromptVersionStatus.PUBLISHED);
        target.setPublishedAt(LocalDateTime.now());
        versionRepository.saveAll(versions);
        versionRepository.save(target);
        template.setPublishedVersion(versionNo);
        templateRepository.save(template);
        return toVersionResponse(template, target);
    }

    /** 使用历史版本发布，提供显式回滚语义。 */
    @Transactional
    public PromptVersionResponse rollback(String roleKey, String templateType, Integer versionNo) {
        return publish(roleKey, templateType, versionNo);
    }

    /** 校验提示词内容和变量白名单。 */
    public void validateContent(String content, List<String> declaredVariables) {
        if (content == null || content.trim().isEmpty()) throw new IllegalArgumentException("提示词内容不能为空");
        Set<String> declared = new LinkedHashSet<>(declaredVariables == null ? extractVariables(content) : declaredVariables);
        for (String variable : declared) {
            if (!DEFAULT_VARIABLES.contains(variable)) {
                throw new IllegalArgumentException("不支持的提示词变量: " + variable);
            }
        }
        Matcher matcher = VARIABLE.matcher(content);
        while (matcher.find()) {
            String variable = matcher.group(1);
            if (!DEFAULT_VARIABLES.contains(variable)) throw new IllegalArgumentException("不支持的提示词变量: " + variable);
            if (!declared.contains(variable)) throw new IllegalArgumentException("未声明的提示词变量: " + variable);
            if (variable.toLowerCase(Locale.ROOT).contains("key") || variable.toLowerCase(Locale.ROOT).contains("token")) {
                throw new IllegalArgumentException("提示词不得引用凭据变量");
            }
        }
    }

    /**
     * 导入 Python/TradingAgents 源码中的默认角色提示词。
     * 仅替换首次生成的通用占位模板；已由人工发布的版本一律保留。
     */
    @Transactional
    public Map<String, Object> importSourceDefaults(JsonNode catalog) {
        if (catalog == null || !catalog.isArray()) {
            throw new IllegalArgumentException("Python 提示词目录格式无效");
        }
        ensureDefaults();
        List<String> imported = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        for (JsonNode item : catalog) {
            String roleKey = item.path("roleKey").asText();
            String templateType = item.path("templateType").asText();
            String content = item.path("content").asText();
            List<String> variables = new ArrayList<>();
            item.path("variables").forEach(value -> variables.add(value.asText()));
            if (!ROLE_KEYS.contains(roleKey) || !"ROLE".equals(templateType)) {
                throw new IllegalArgumentException("Python 提示词目录包含不支持的角色: " + roleKey);
            }
            validateContent(content, variables);
            PromptTemplate template = getTemplate(roleKey, templateType);
            PromptVersion published = versionRepository.findByTemplateIdAndStatus(
                    template.getId(), PromptVersionStatus.PUBLISHED).orElse(null);
            if (published != null && !isGenericDefault(roleKey, published.getContent())) {
                skipped.add(roleKey);
                continue;
            }
            int nextVersion = versionRepository.findByTemplateIdOrderByVersionNoDesc(template.getId()).stream()
                    .mapToInt(version -> version.getVersionNo() == null ? 0 : version.getVersionNo())
                    .max().orElse(0) + 1;
            for (PromptVersion version : versionRepository.findByTemplateIdOrderByVersionNoDesc(template.getId())) {
                if (version.getStatus() == PromptVersionStatus.PUBLISHED) {
                    version.setStatus(PromptVersionStatus.ARCHIVED);
                }
            }
            PromptVersion version = new PromptVersion();
            version.setTemplateId(template.getId());
            version.setVersionNo(nextVersion);
            version.setContent(content);
            version.setVariablesJson(toJson(variables));
            version.setContentHash(hash(content));
            version.setStatus(PromptVersionStatus.PUBLISHED);
            version.setPublishedAt(LocalDateTime.now());
            versionRepository.save(version);
            template.setPublishedVersion(nextVersion);
            template.setDescription("从 Python TradingAgents 源码同步的角色提示词");
            templateRepository.save(template);
            imported.add(roleKey);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", imported);
        result.put("skipped", skipped);
        result.put("importedCount", imported.size());
        result.put("skippedCount", skipped.size());
        return result;
    }

    /** 为作业复制当前发布版本，返回快照内容和不可变指纹。 */
    @Transactional
    public Map<String, Object> snapshot(String jobId) {
        ensureDefaults();
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, AnalysisJobPromptSnapshot> existing = new HashMap<>();
        for (AnalysisJobPromptSnapshot item : snapshotRepository.findByJobIdOrderByRoleKeyAsc(jobId)) {
            existing.put(item.getRoleKey() + "\u0000" + item.getTemplateType(), item);
        }
        for (PromptTemplate template : templateRepository.findAll()) {
            String snapshotKey = template.getRoleKey() + "\u0000" + template.getTemplateType();
            PromptVersion version = versionRepository.findByTemplateIdAndStatus(template.getId(), PromptVersionStatus.PUBLISHED)
                    .orElseThrow(() -> new IllegalStateException("角色没有已发布提示词: " + template.getRoleKey()));
            // 通用占位模板不是 Python 的真实角色提示词；不下发即可让 Python 保持源码默认行为。
            if ("ROLE".equals(template.getTemplateType()) && isGenericDefault(template.getRoleKey(), version.getContent())) {
                continue;
            }
            AnalysisJobPromptSnapshot snapshot = existing.get(snapshotKey);
            if (snapshot == null) {
                snapshot = new AnalysisJobPromptSnapshot();
                snapshot.setJobId(jobId);
                snapshot.setRoleKey(template.getRoleKey());
                snapshot.setTemplateType(template.getTemplateType());
                snapshot.setVersionId(version.getId());
                snapshot.setVersionNo(version.getVersionNo());
                snapshot.setContent(version.getContent());
                snapshot.setContentHash(version.getContentHash());
                snapshotRepository.save(snapshot);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleKey", template.getRoleKey());
            row.put("templateType", template.getTemplateType());
            row.put("version", snapshot.getVersionNo());
            row.put("content", snapshot.getContent());
            row.put("contentHash", snapshot.getContentHash());
            rows.add(row);
        }
        result.put("templates", rows);
        result.put("hash", hash(toJson(rows)));
        return result;
    }

    private PromptTemplate getTemplate(String roleKey, String templateType) {
        validateRole(roleKey);
        return templateRepository.findByRoleKeyAndTemplateType(roleKey, templateType)
                .orElseThrow(() -> new IllegalArgumentException("提示词模板不存在"));
    }

    private PromptTemplate createTemplate(String roleKey, String templateType) {
        PromptTemplate template = new PromptTemplate();
        template.setRoleKey(roleKey);
        template.setTemplateType(templateType);
        template.setDescription("TradingAgents 角色提示词");
        return templateRepository.save(template);
    }

    private void validateRole(String roleKey) {
        if (!ROLE_KEYS.contains(roleKey) && !roleKey.startsWith("tool_") && !roleKey.startsWith("output_")) {
            throw new IllegalArgumentException("不支持的角色或模板: " + roleKey);
        }
    }

    private PromptTemplateResponse toTemplateResponse(PromptTemplate template) {
        PromptTemplateResponse response = new PromptTemplateResponse();
        response.setId(template.getId()); response.setRoleKey(template.getRoleKey());
        response.setTemplateType(template.getTemplateType()); response.setDescription(template.getDescription());
        response.setPublishedVersion(template.getPublishedVersion());
        return response;
    }

    private PromptVersionResponse toVersionResponse(PromptTemplate template, PromptVersion version) {
        PromptVersionResponse response = new PromptVersionResponse();
        response.setId(version.getId()); response.setRoleKey(template.getRoleKey());
        response.setTemplateType(template.getTemplateType()); response.setVersion(version.getVersionNo());
        response.setContent(version.getContent()); response.setStatus(version.getStatus().name());
        response.setContentHash(version.getContentHash()); response.setCreatedAt(version.getCreatedAt());
        response.setPublishedAt(version.getPublishedAt());
        try { response.setVariables(objectMapper.readValue(version.getVariablesJson(), new TypeReference<List<String>>() {})); }
        catch (Exception ignored) { response.setVariables(Collections.emptyList()); }
        return response;
    }

    private List<String> extractVariables(String content) {
        Set<String> variables = new LinkedHashSet<>();
        Matcher matcher = VARIABLE.matcher(content);
        while (matcher.find()) variables.add(matcher.group(1));
        return new ArrayList<>(variables);
    }

    private String defaultContent(String role) {
        return "你是 A 股投研系统中的 " + role + " 角色。请基于 {{ticker}} 在 {{date}} 的数据，给出可审计、简洁且结构化的分析。";
    }

    /** 判断是否为系统首次启动生成的通用占位模板。 */
    private boolean isGenericDefault(String role, String content) {
        return defaultContent(role).equals(content);
    }

    private String toJson(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new IllegalStateException("序列化提示词配置失败", exception); }
    }

    private String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : digest) builder.append(String.format("%02x", item));
            return builder.toString();
        } catch (Exception exception) { throw new IllegalStateException("计算提示词指纹失败", exception); }
    }
}
