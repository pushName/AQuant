package com.brotherc.aquant.llm.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 提示词模板的不可变版本记录。 */
@Data
@Entity
@Table(name = "llm_prompt_version", uniqueConstraints = @UniqueConstraint(
        name = "uk_llm_prompt_version", columnNames = {"template_id", "version_no"}))
public class PromptVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    @Lob
    @Column(name = "variables_json", columnDefinition = "TEXT")
    private String variablesJson;

    @Column(name = "content_hash", nullable = false, length = 128)
    private String contentHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PromptVersionStatus status;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (status == null) status = PromptVersionStatus.DRAFT;
    }
}
