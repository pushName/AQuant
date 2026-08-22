package com.brotherc.aquant.llm.repository;

import com.brotherc.aquant.llm.entity.AnalysisJob;
import com.brotherc.aquant.llm.entity.AnalysisJobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/** 分析作业数据访问接口。 */
public interface AnalysisJobRepository extends JpaRepository<AnalysisJob, String> {
    Page<AnalysisJob> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<AnalysisJob> findByStatusIn(Collection<AnalysisJobStatus> statuses);
}
