package com.brotherc.aquant.llm.entity;

import jakarta.persistence.*;
import lombok.Data;

/** 作业启动时使用的提示词不可变快照。 */
@Data
@Entity
@Table(name = "analysis_job_prompt_snapshot", uniqueConstraints = @UniqueConstraint(
        name = "uk_analysis_job_prompt_snapshot", columnNames = {"job_id", "role_key", "template_type"}))
public class AnalysisJobPromptSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, length = 64)
    private String jobId;

    @Column(name = "role_key", nullable = false, length = 80)
    private String roleKey;

    @Column(name = "template_type", nullable = false, length = 30)
    private String templateType;

    @Column(name = "version_id")
    private Long versionId;

    @Column(name = "version_no")
    private Integer versionNo;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;
}
