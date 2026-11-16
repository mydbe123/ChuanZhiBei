package com.zhixue.engine.plan;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.analytics.AiUsageService;
import com.zhixue.engine.analytics.LearningEventService;
import com.zhixue.engine.common.BusinessException;
import com.zhixue.engine.llm.StructuredGenerator;
import com.zhixue.engine.plan.PlanDtos.DayGroup;
import com.zhixue.engine.plan.PlanDtos.PlanDetail;
import com.zhixue.engine.plan.PlanDtos.PlanSummary;
import com.zhixue.engine.plan.PlanDtos.TaskView;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class PlanService {

    private static final String SYSTEM_PROMPT = """
            你是「智学引擎」的学习路径规划专家，熟悉高校计算机课程与考研/竞赛备考节奏。
            请把模糊的学习目标拆解为可执行、可打卡的每日任务。
            硬性要求：
            1) 只输出一个 JSON 对象，不要输出 markdown 代码块或任何解释文字；
            2) 输出结构：
            {"title":"计划标题","summary":"60 字以内的整体策略说明","days":[{"day":1,"tasks":[{"title":"任务标题","detail":"具体做法与验收标准","knowledgePoints":["知识点"],"minutes":30}]}]}
            3) days 数量必须等于用户要求的天数，day 从 1 开始递增；
            4) 每天任务总时长应接近用户可投入时长，单个任务 15-60 分钟；
            5) 任务标题以动词开头且具体（例如"默写二叉树前中后序遍历并手推 3 个用例"）；
            6) 难度逐日递进：第 1 天建立知识地图，中间刻意练习，最后一天综合自测与复盘。
            """;

    private final StudyPlanRepository plans;
    private final StudyTaskRepository tasks;
    private final StructuredGenerator generator;
    private final LearningEventService learningEvents;
    private final AiUsageService aiUsage;

    public PlanService(StudyPlanRepository plans, StudyTaskRepository tasks, StructuredGenerator generator,
                       LearningEventService learningEvents, AiUsageService aiUsage) {
        this.plans = plans;
        this.tasks = tasks;
        this.generator = generator;
        this.learningEvents = learningEvents;
        this.aiUsage = aiUsage;
    }

    public PlanDetail generate(long userId, String goal, String level, Integer days, Integer dailyMinutes) {
        String safeGoal = goal == null || goal.isBlank() ? "系统掌握数据结构与算法核心知识" : goal.trim();
        String safeLevel = normalizeLevel(level);
        int duration = days == null ? 7 : Math.max(3, Math.min(30, days));
        int minutes = dailyMinutes == null ? 60 : Math.max(20, Math.min(300, dailyMinutes));

        String userPrompt = """
                学习目标：%s
                当前水平：%s（BEGINNER=零基础，INTERMEDIATE=有基础，ADVANCED=进阶）
                计划天数：%d
                每天可投入：%d 分钟
                请生成 JSON。
                """.formatted(safeGoal, safeLevel, duration, minutes);

        StructuredGenerator.Result result = generator.generate("plan-generate", SYSTEM_PROMPT, userPrompt);
        aiUsage.record(userId, "PLAN_GENERATE", result.model(), result.promptTokens(),
                result.completionTokens(), result.latencyMs(), "OK", false, safeGoal);

        JsonNode root = result.data();
        JsonNode dayNodes = root.path("days");
        if (!dayNodes.isArray() || dayNodes.isEmpty()) {
            throw new BusinessException(500, "AI 未能生成有效的学习计划，请稍后重试");
        }

        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        String title = root.path("title").asText("");
        plan.setTitle(title.isBlank() ? safeGoal + " · AI 学习计划" : truncate(title, 200));
        plan.setGoal(truncate(safeGoal, 255));
        plan.setLevel(safeLevel);
        plan.setDurationDays(duration);
        plan.setDailyMinutes(minutes);
        plan.setSummary(root.path("summary").asText(""));
        plans.save(plan);

        List<StudyTask> entities = new ArrayList<>();
        int dayIndex = 1;
        for (JsonNode dayNode : dayNodes) {
            int day = dayNode.path("day").asInt(dayIndex);
            int seq = 1;
            for (JsonNode taskNode : dayNode.path("tasks")) {
                String taskTitle = taskNode.path("title").asText("").trim();
                if (taskTitle.isBlank()) {
                    continue;
                }
                StudyTask task = new StudyTask();
                task.setPlanId(plan.getId());
                task.setDayIndex(day);
                task.setSeq(seq++);
                task.setTitle(truncate(taskTitle, 200));
                task.setDetail(taskNode.path("detail").asText(""));
                task.setKnowledgePoints(joinKnowledgePoints(taskNode.path("knowledgePoints")));
                task.setMinutes(Math.max(10, Math.min(180, taskNode.path("minutes").asInt(30))));
                entities.add(task);
            }
            dayIndex++;
        }
        if (entities.isEmpty()) {
            throw new BusinessException(500, "AI 返回的计划格式不正确，请重试");
        }
        tasks.saveAll(entities);
        return detail(userId, plan.getId());
    }

    public List<PlanSummary> list(long userId) {
        return plans.findByUserIdOrderByIdDesc(userId).stream().map(this::toSummary).toList();
    }

    public PlanDetail detail(long userId, long planId) {
        StudyPlan plan = requireOwned(planId, userId);
        List<StudyTask> list = tasks.findByPlanIdOrderByDayIndexAscSeqAsc(planId);
        Map<Integer, List<TaskView>> grouped = new LinkedHashMap<>();
        for (StudyTask task : list) {
            grouped.computeIfAbsent(task.getDayIndex(), key -> new ArrayList<>())
                    .add(new TaskView(task.getId(), task.getDayIndex(), task.getSeq(), task.getTitle(),
                            task.getDetail(), splitKnowledgePoints(task.getKnowledgePoints()),
                            task.getMinutes(), task.getStatus(), task.getCompletedAt()));
        }
        List<DayGroup> days = grouped.entrySet().stream()
                .map(e -> new DayGroup(e.getKey(),
                        e.getValue().stream().mapToInt(TaskView::minutes).sum(), e.getValue()))
                .toList();
        return new PlanDetail(toSummary(plan), days);
    }

    public PlanDetail updateTaskStatus(long userId, long taskId, String status) {
        StudyTask task = tasks.findById(taskId).orElseThrow(() -> new BusinessException(404, "任务不存在"));
        StudyPlan plan = requireOwned(task.getPlanId(), userId);
        boolean done = "DONE".equalsIgnoreCase(status);
        String previous = task.getStatus();
        task.setStatus(done ? "DONE" : "TODO");
        task.setCompletedAt(done ? LocalDateTime.now() : null);
        tasks.save(task);
        if (done && !"DONE".equals(previous)) {
            learningEvents.record(userId, "PLAN_TASK", "STUDY_TASK", task.getId(), null, null,
                    task.getMinutes(), task.getTitle());
        }
        return detail(userId, plan.getId());
    }

    private PlanSummary toSummary(StudyPlan plan) {
        long total = tasks.countByPlanId(plan.getId());
        long done = tasks.countByPlanIdAndStatus(plan.getId(), "DONE");
        int progress = total == 0 ? 0 : (int) Math.round(done * 100.0 / total);
        return new PlanSummary(plan.getId(), plan.getTitle(), plan.getGoal(), plan.getLevel(),
                plan.getDurationDays(), plan.getDailyMinutes(), plan.getSummary(),
                (int) total, (int) done, progress, plan.getCreatedAt());
    }

    private StudyPlan requireOwned(long planId, long userId) {
        StudyPlan plan = plans.findById(planId).orElseThrow(() -> new BusinessException(404, "学习计划不存在"));
        if (plan.getUserId() != userId) {
            throw new BusinessException(403, "无权访问他人的学习计划");
        }
        return plan;
    }

    private String joinKnowledgePoints(JsonNode node) {
        List<String> points = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> {
                String text = item.asText("").trim();
                if (!text.isBlank()) {
                    points.add(truncate(text, 30));
                }
            });
        }
        return String.join(",", points);
    }

    private List<String> splitKnowledgePoints(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return List.of(raw.split(","));
    }

    private String normalizeLevel(String level) {
        if (level == null) {
            return "BEGINNER";
        }
        String value = level.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "BEGINNER", "INTERMEDIATE", "ADVANCED" -> value;
            default -> "BEGINNER";
        };
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
