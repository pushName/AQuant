package com.brotherc.aquant.strategy.model.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Schema(description = "股票策略回测详情请求")
public class StockStrategyBacktestDetailReqVO {

    @NotBlank(message = "股票代码不能为空")
    private String code;

    @NotBlank(message = "策略类型不能为空")
    @Schema(description = "策略类型：DUAL_MA、MOMENTUM、MACD、GRID")
    private String strategyType;

    @NotNull(message = "回测年数不能为空")
    private Integer recentYears = 2;

    @Schema(description = "自定义回测开始日期，与结束日期同时传入", example = "2024-09-06")
    private LocalDate startDate;

    @Schema(description = "自定义回测结束日期，与开始日期同时传入", example = "2026-09-06")
    private LocalDate endDate;

    private Integer maShort = 5;

    private Integer maLong = 20;

    private Integer lookbackDays = 20;

    private Integer fastPeriod = 12;

    private Integer slowPeriod = 26;

    private Integer signalPeriod = 9;

    private BigDecimal gridRate = new BigDecimal("0.03");

    private Integer gridCount = 5;

}
