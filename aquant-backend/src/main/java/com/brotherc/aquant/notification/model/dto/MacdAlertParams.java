package com.brotherc.aquant.notification.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * MACD 通知参数。
 */
@Getter
@AllArgsConstructor
public class MacdAlertParams {

    private String direction;

    private int fastPeriod;

    private int slowPeriod;

    private int signalPeriod;

}
