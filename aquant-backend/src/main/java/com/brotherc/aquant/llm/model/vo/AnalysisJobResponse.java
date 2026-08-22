package com.brotherc.aquant.llm.model.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** 分析作业页面展示对象。 */
@Data
public class AnalysisJobResponse {
    private String id;
    private String status;
    private String stage;
    private LocalDate date;
    private List<String> tickers;
    private Integer total;
    private Integer completed;
    private Integer failed;
    private Integer progress;
    private String pythonJobId;
    private String errorMessage;
    private Boolean cancelRequested;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
