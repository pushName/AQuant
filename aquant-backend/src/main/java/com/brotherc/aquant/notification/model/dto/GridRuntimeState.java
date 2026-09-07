package com.brotherc.aquant.notification.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 单条网格通知在轮询期间的运行状态。
 */
@Getter
@Setter
@AllArgsConstructor
public class GridRuntimeState {

    private String historyAnchor;

    private String params;

    private BigDecimal referencePrice;

    private int positionLevel;

    private long expiredAtMillis;
}
