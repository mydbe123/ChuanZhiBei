package com.zhixue.engine.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiUsageLogRepository extends JpaRepository<AiUsageLog, Long> {

    List<AiUsageLog> findTop25ByUserIdOrderByIdDesc(long userId);

    @Query("""
            select count(l) as calls,
                   coalesce(sum(l.promptTokens), 0) as promptTokens,
                   coalesce(sum(l.completionTokens), 0) as completionTokens,
                   coalesce(sum(case when l.blocked = true then 1 else 0 end), 0) as blocked
            from AiUsageLog l where l.userId = :userId
            """)
    AiSummary summary(@Param("userId") long userId);

    interface AiSummary {
        Long getCalls();

        Long getPromptTokens();

        Long getCompletionTokens();

        Long getBlocked();
    }
}
