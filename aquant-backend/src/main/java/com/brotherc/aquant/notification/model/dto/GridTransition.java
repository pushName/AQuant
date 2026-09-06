package com.brotherc.aquant.notification.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 单次行情检测产生的网格跨越结果。
 */
@Getter
@AllArgsConstructor
public class GridTransition {

    private String direction;

    private BigDecimal triggerPrice;

    private int crossedLevels;

    private int positionLevel;
}
