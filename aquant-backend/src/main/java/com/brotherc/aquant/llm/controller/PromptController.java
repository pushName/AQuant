package com.brotherc.aquant.llm.controller;

import com.brotherc.aquant.common.model.dto.ResponseDTO;
import com.brotherc.aquant.llm.model.vo.PromptDraftRequest;
import com.brotherc.aquant.llm.service.PythonAnalysisClient;
import com.brotherc.aquant.llm.service.PromptTemplateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

/** LLM 角色、工具和输出模板管理接口。 */
@RestController
@RequestMapping("/analysis/prompts")
public class PromptController {
    private final PromptTemplateService service;
    private final PythonAnalysisClient pythonAnalysisClient;

    public PromptController(PromptTemplateService service, PythonAnalysisClient pythonAnalysisClient) {
        this.service = service;
        this.pythonAnalysisClient = pythonAnalysisClient;
    }

    @GetMapping
    public ResponseDTO<?> list() { return ResponseDTO.success(service.listTemplates()); }

    @GetMapping("/{roleKey}/versions")
    public ResponseDTO<?> versions(@PathVariable String roleKey,
                                   @RequestParam(defaultValue = "ROLE") String templateType) {
        return ResponseDTO.success(service.listVersions(roleKey, templateType));
    }

    @GetMapping("/{roleKey}/versions/{version}")
    public ResponseDTO<?> version(@PathVariable String roleKey,
                                  @PathVariable Integer version,
                                  @RequestParam(defaultValue = "ROLE") String templateType) {
        return ResponseDTO.success(service.getVersion(roleKey, templateType, version));
    }

    @PostMapping("/{roleKey}/draft")
    public ResponseDTO<?> draft(@PathVariable String roleKey,
                                @RequestParam(defaultValue = "ROLE") String templateType,
                                @Valid @RequestBody PromptDraftRequest request) {
        return ResponseDTO.success(service.saveDraft(roleKey, templateType, request));
    }

    @PutMapping("/{roleKey}/draft/{version}")
    public ResponseDTO<?> updateDraft(@PathVariable String roleKey,
                                      @PathVariable Integer version,
                                      @RequestParam(defaultValue = "ROLE") String templateType,
                                      @Valid @RequestBody PromptDraftRequest request) {
        return ResponseDTO.success(service.updateDraft(roleKey, templateType, version, request));
    }

    @PostMapping("/{roleKey}/versions/{version}/publish")
    public ResponseDTO<?> publish(@PathVariable String roleKey,
                                  @PathVariable Integer version,
                                  @RequestParam(defaultValue = "ROLE") String templateType) {
        return ResponseDTO.success(service.publish(roleKey, templateType, version));
    }

    @PostMapping("/{roleKey}/versions/{version}/rollback")
    public ResponseDTO<?> rollback(@PathVariable String roleKey,
                                   @PathVariable Integer version,
                                   @RequestParam(defaultValue = "ROLE") String templateType) {
        return ResponseDTO.success(service.rollback(roleKey, templateType, version));
    }

    @PostMapping("/validate")
    public ResponseDTO<?> validate(@Valid @RequestBody PromptDraftRequest request) {
        service.validateContent(request.getContent(), request.getVariables());
        return ResponseDTO.success("提示词校验通过", null);
    }

    /**
     * 从已启动的 Python 分析服务导入当前 TradingAgents 源码提示词。
     * 只替换系统通用占位模板，已人工发布的版本不会被覆盖。
     */
    @PostMapping("/import-source-defaults")
    public ResponseDTO<?> importSourceDefaults() {
        return ResponseDTO.success(service.importSourceDefaults(pythonAnalysisClient.promptCatalog()));
    }

    @GetMapping("/roles")
    public ResponseDTO<?> roles() { return ResponseDTO.success(PromptTemplateService.ROLE_KEYS); }
}
