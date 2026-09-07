package com.brotherc.aquant.notification.model.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

/**
 * 单条 MACD 通知在轮询期间的运行状态。
 */
@Getter
@Setter
@AllArgsConstructor
public class MacdRuntimeState {

    private String historyAnchor;

    private String params;

    private int relation;

    private long expiredAtMillis;

}
