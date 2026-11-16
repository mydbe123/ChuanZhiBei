package com.zhixue.engine.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface LearningEventRepository extends JpaRepository<LearningEvent, Long> {

    List<LearningEvent> findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(long userId, LocalDateTime after);

    List<LearningEvent> findTop8ByUserIdOrderByCreatedAtDesc(long userId);

    @Query("""
            select e.knowledgePoint, avg(e.score), count(e)
            from LearningEvent e
            where e.userId = :userId and e.knowledgePoint is not null and e.score is not null
            group by e.knowledgePoint
            """)
    List<Object[]> masteryByKnowledgePoint(@Param("userId") long userId);

    @Query("select coalesce(sum(e.minutes), 0) from LearningEvent e where e.userId = :userId and e.createdAt >= :from")
    long sumMinutesSince(@Param("userId") long userId, @Param("from") LocalDateTime from);
}
