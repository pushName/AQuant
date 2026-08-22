package com.brotherc.aquant.llm.model.vo;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** 创建智能分析作业请求。 */
@Data
public class AnalysisJobCreateRequest {
    @NotNull
    private LocalDate date;

    @NotEmpty
    private List<String> tickers;

    private Boolean skipKronos = false;
    private Boolean streaming = false;
    private Long promptReleaseId;
    private Map<String, Object> modelConfig;
}
