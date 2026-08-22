package com.brotherc.aquant.llm.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 角色、工具或输出格式模板的逻辑定义。 */
@Data
@Entity
@Table(name = "llm_prompt_template", uniqueConstraints = @UniqueConstraint(
        name = "uk_llm_prompt_template_key_type", columnNames = {"role_key", "template_type"}))
public class PromptTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_key", nullable = false, length = 80)
    private String roleKey;

    @Column(name = "template_type", nullable = false, length = 30)
    private String templateType;

    @Column(length = 200)
    private String description;

    @Column(name = "published_version")
    private Integer publishedVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
