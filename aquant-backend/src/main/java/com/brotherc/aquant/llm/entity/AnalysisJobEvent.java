package com.brotherc.aquant.llm.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 分析作业事件，供页面轮询和 SSE 断线恢复使用。 */
@Data
@Entity
@Table(name = "analysis_job_event", uniqueConstraints = {
        @UniqueConstraint(name = "uk_analysis_job_event_seq", columnNames = {"job_id", "sequence_no"})
}, indexes = @Index(name = "idx_analysis_job_event_job", columnList = "job_id,sequence_no"))
public class AnalysisJobEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "sequence_no", nullable = false)
    private Long sequenceNo;

    /** Python 事件序号，仅用于幂等去重；页面序号始终使用本地序号。 */
    @Column(name = "source_seq")
    private Long sourceSeq;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(length = 30)
    private String stage;

    @Column(length = 80)
    private String role;

    @Column(length = 80)
    private String ticker;

    @Column(length = 30)
    private String status;

    private Integer completed;
    private Integer total;

    @Column(length = 1000)
    private String message;

    @Lob
    @Column(name = "payload_json", columnDefinition = "LONGTEXT")
    private String payloadJson;

    @Column(name = "event_at", nullable = false)
    private LocalDateTime eventAt;

    @PrePersist
    protected void onCreate() {
        if (eventAt == null) eventAt = LocalDateTime.now();
    }
}
