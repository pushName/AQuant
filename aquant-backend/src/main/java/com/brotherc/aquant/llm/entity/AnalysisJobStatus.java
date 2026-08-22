package com.brotherc.aquant.llm.entity;

/** 分析作业生命周期状态。 */
public enum AnalysisJobStatus {
    QUEUED,
    RUNNING,
    CANCELLING,
    CANCELLED,
    SUCCEEDED,
    FAILED
}
