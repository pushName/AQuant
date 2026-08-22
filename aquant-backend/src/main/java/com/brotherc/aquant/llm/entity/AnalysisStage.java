package com.brotherc.aquant.llm.entity;

/** 分析流水线阶段。 */
public enum AnalysisStage {
    DATA_PREPARING,
    TA_ANALYSTS,
    KRONOS,
    MERGING,
    COMMITTEE,
    PERSISTING,
    COMPLETED
}
