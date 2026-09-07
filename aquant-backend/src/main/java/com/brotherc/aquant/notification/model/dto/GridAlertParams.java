package com.brotherc.aquant.notification.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

/**
 * 网格通知参数。
 */
@Getter
@AllArgsConstructor
public class GridAlertParams {

    private String direction;

    private BigDecimal gridRate;

    private int gridCount;
}
