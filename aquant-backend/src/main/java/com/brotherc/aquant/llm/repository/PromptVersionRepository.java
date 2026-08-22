package com.brotherc.aquant.llm.repository;

import com.brotherc.aquant.llm.entity.PromptVersion;
import com.brotherc.aquant.llm.entity.PromptVersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/** 提示词版本数据访问接口。 */
public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {
    List<PromptVersion> findByTemplateIdOrderByVersionNoDesc(Long templateId);
    Optional<PromptVersion> findByTemplateIdAndVersionNo(Long templateId, Integer versionNo);
    Optional<PromptVersion> findByTemplateIdAndStatus(Long templateId, PromptVersionStatus status);

    /** 直接写入默认 v1 版本，命中 (template_id, version_no) 唯一键则静默忽略，保证并发初始化幂等。 */
    @Modifying
    @Query(value = "INSERT IGNORE INTO llm_prompt_version "
            + "(template_id, version_no, content, variables_json, content_hash, status, created_at, published_at) "
            + "VALUES (:templateId, 1, :content, :variablesJson, :contentHash, 'PUBLISHED', NOW(), NOW())",
            nativeQuery = true)
    int insertDefaultIfAbsent(@Param("templateId") Long templateId,
                              @Param("content") String content,
                              @Param("variablesJson") String variablesJson,
                              @Param("contentHash") String contentHash);
}
