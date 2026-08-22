package com.brotherc.aquant.llm.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** 持久化分析作业及其可恢复状态。 */
@Data
@Entity
@Table(name = "analysis_job", indexes = {
        @Index(name = "idx_analysis_job_status", columnList = "status"),
        @Index(name = "idx_analysis_job_created_at", columnList = "created_at")
})
public class AnalysisJob {
    @Id
    @Column(length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisJobStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AnalysisStage stage;

    @Column(name = "analysis_date", nullable = false)
    private LocalDate analysisDate;

    @Lob
    @Column(name = "tickers_json", nullable = false, columnDefinition = "LONGTEXT")
    private String tickersJson;

    @Column(name = "total_count", nullable = false)
    private Integer totalCount = 0;

    @Column(name = "completed_count", nullable = false)
    private Integer completedCount = 0;

    @Column(name = "failed_count", nullable = false)
    private Integer failedCount = 0;

    @Column(nullable = false)
    private Integer progress = 0;

    @Column(name = "python_job_id", length = 64)
    private String pythonJobId;

    @Column(name = "prompt_release_id")
    private Long promptReleaseId;

    @Column(name = "prompt_snapshot_hash", length = 128)
    private String promptSnapshotHash;

    @Lob
    @Column(name = "config_json", columnDefinition = "LONGTEXT")
    private String configJson;

    @Lob
    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_username", length = 100)
    private String createdUsername;

    @Column(name = "cancel_requested", nullable = false)
    private Boolean cancelRequested = false;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @PrePersist
    protected void onCreate() {
        if (id == null) id = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null) status = AnalysisJobStatus.QUEUED;
        if (stage == null) stage = AnalysisStage.DATA_PREPARING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
