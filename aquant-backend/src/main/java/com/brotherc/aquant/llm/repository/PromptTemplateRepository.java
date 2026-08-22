package com.brotherc.aquant.llm.repository;

import com.brotherc.aquant.llm.entity.PromptTemplate;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/** 提示词模板数据访问接口。 */
public interface PromptTemplateRepository extends JpaRepository<PromptTemplate, Long> {
    Optional<PromptTemplate> findByRoleKeyAndTemplateType(String roleKey, String templateType);

    /** 并发初始化时直接插入，命中唯一键则静默忽略，避免先查后插的竞态。 */
    @Modifying
    @Query(value = "INSERT IGNORE INTO llm_prompt_template (role_key, template_type, description, created_at, updated_at) "
            + "VALUES (:roleKey, :templateType, :description, NOW(), NOW())", nativeQuery = true)
    int insertRoleTemplateIfAbsent(@Param("roleKey") String roleKey,
                                   @Param("templateType") String templateType,
                                   @Param("description") String description);

    /** 加锁读最新已提交状态，规避可重复读快照看不到并发提交的行。 */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from PromptTemplate t where t.roleKey = :roleKey and t.templateType = :templateType")
    Optional<PromptTemplate> findByRoleKeyAndTemplateTypeForUpdate(@Param("roleKey") String roleKey,
                                                                   @Param("templateType") String templateType);
}
