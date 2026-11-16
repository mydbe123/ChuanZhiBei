package com.zhixue.engine.analytics;

import java.time.LocalDateTime;
import java.util.List;

public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    public record Stats(int studyMinutes, int activeDays, int streakDays, long quizzes, int avgScore,
                        int documents, int chunks, long aiCalls) {
    }

    public record DayActivity(String date, String label, int minutes, int events) {
    }

    public record MasteryPoint(String point, int mastery, int attempts) {
    }

    public record Activity(String type, String title, String detail, LocalDateTime at) {
    }

    public record AiStats(long calls, long tokens, long blocked) {
    }

    public record Overview(Stats stats, List<DayActivity> weekly, List<MasteryPoint> mastery,
                           List<MasteryPoint> weakPoints, List<Activity> recent, AiStats ai,
                           String insight) {
    }

    public record AiUsageItem(long id, String scene, String model, int promptTokens, int completionTokens,
                              int latencyMs, String status, boolean blocked, String detail,
                              LocalDateTime createdAt) {
        public static AiUsageItem of(AiUsageLog log) {
            return new AiUsageItem(log.getId(), log.getScene(), log.getModel(), log.getPromptTokens(),
                    log.getCompletionTokens(), log.getLatencyMs(), log.getStatus(), log.isBlocked(),
                    log.getDetail(), log.getCreatedAt());
        }
    }
}
