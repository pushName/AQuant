package com.brotherc.aquant.llm.model.vo;

import lombok.Data;

/** 提示词模板概要。 */
@Data
public class PromptTemplateResponse {
    private Long id;
    private String roleKey;
    private String templateType;
    private String description;
    private Integer publishedVersion;
}
