package com.brotherc.aquant.llm.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

/** 作业事件页面展示对象。 */
@Data
public class AnalysisJobEventResponse {
    private Long seq;
    private String type;
    private String stage;
    private String role;
    private String ticker;
    private String status;
    private Integer completed;
    private Integer total;
    private String message;
    private String payload;
    private LocalDateTime timestamp;
}
