package com.brotherc.aquant.llm.model.vo;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/** 提示词草稿请求。 */
@Data
public class PromptDraftRequest {
    @NotBlank
    private String content;
    private List<String> variables;
    private String description;
}
