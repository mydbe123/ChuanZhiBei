package com.zhixue.engine.plan;

import java.time.LocalDateTime;
import java.util.List;

public final class PlanDtos {

    private PlanDtos() {
    }

    public record TaskView(long id, int day, int seq, String title, String detail,
                           List<String> knowledgePoints, int minutes, String status,
                           LocalDateTime completedAt) {
    }

    public record DayGroup(int day, int totalMinutes, List<TaskView> tasks) {
    }

    public record PlanSummary(long id, String title, String goal, String level, int durationDays,
                              int dailyMinutes, String summary, int totalTasks, int doneTasks,
                              int progress, LocalDateTime createdAt) {
    }

    public record PlanDetail(PlanSummary plan, List<DayGroup> days) {
    }

    public record GenerateRequest(String goal, String level, Integer days, Integer dailyMinutes) {
    }

    public record TaskStatusRequest(String status) {
    }
}
