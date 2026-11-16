package com.zhixue.engine.plan;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudyTaskRepository extends JpaRepository<StudyTask, Long> {

    List<StudyTask> findByPlanIdOrderByDayIndexAscSeqAsc(long planId);

    long countByPlanId(long planId);

    long countByPlanIdAndStatus(long planId, String status);
}
