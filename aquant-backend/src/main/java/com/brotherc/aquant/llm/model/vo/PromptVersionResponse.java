package com.brotherc.aquant.llm.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 提示词版本页面展示对象。 */
@Data
public class PromptVersionResponse {
    private Long id;
    private String roleKey;
    private String templateType;
    private Integer version;
    private String content;
    private List<String> variables;
    private String status;
    private String contentHash;
    private LocalDateTime createdAt;
    private LocalDateTime publishedAt;
}
