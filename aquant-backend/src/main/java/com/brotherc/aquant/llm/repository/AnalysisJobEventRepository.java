package com.brotherc.aquant.llm.repository;

import com.brotherc.aquant.llm.entity.AnalysisJobEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** 作业事件数据访问接口。 */
public interface AnalysisJobEventRepository extends JpaRepository<AnalysisJobEvent, Long> {
    List<AnalysisJobEvent> findByJobIdAndSequenceNoGreaterThanOrderBySequenceNoAsc(String jobId, Long sequenceNo);
    Optional<AnalysisJobEvent> findTopByJobIdOrderBySequenceNoDesc(String jobId);
    Optional<AnalysisJobEvent> findFirstByJobIdAndSourceSeq(String jobId, Long sourceSeq);
}
