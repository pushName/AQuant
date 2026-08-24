package com.brotherc.aquant.llm.repository;

import com.brotherc.aquant.llm.entity.AnalysisJobPromptSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** 作业提示词快照数据访问接口。 */
public interface AnalysisJobPromptSnapshotRepository extends JpaRepository<AnalysisJobPromptSnapshot, Long> {
    List<AnalysisJobPromptSnapshot> findByJobIdOrderByRoleKeyAsc(String jobId);
    void deleteByJobId(String jobId);
}
