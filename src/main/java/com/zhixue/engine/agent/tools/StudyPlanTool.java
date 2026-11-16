package com.zhixue.engine.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.agent.AgentTool;
import com.zhixue.engine.agent.ToolContext;
import com.zhixue.engine.agent.ToolResult;
import com.zhixue.engine.plan.PlanDtos.PlanDetail;
import com.zhixue.engine.plan.PlanService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class StudyPlanTool implements AgentTool {

    private final PlanService planService;

    public StudyPlanTool(PlanService planService) {
        this.planService = planService;
    }

    @Override
    public String name() {
        return "create_study_plan";
    }

    @Override
    public String description() {
        return "根据学习目标生成可打卡的每日学习计划，并保存到「学习计划」模块。"
                + "当学生说「帮我规划」「X 天学会 Y」「制定复习计划」时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("goal", Map.of("type", "string", "description", "具体学习目标，例如「两周掌握二叉树与图的基础算法」"));
        properties.put("days", Map.of("type", "integer", "description", "计划天数，3-30，默认 7"));
        properties.put("dailyMinutes", Map.of("type", "integer", "description", "每天可投入分钟数，默认 60"));
        properties.put("level", Map.of("type", "string", "enum", List.of("BEGINNER", "INTERMEDIATE", "ADVANCED"),
                "description", "当前水平"));
        return Map.of("type", "object", "properties", properties, "required", List.of("goal"));
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String goal = arguments.path("goal").asText("系统掌握课程核心知识");
        Integer days = arguments.hasNonNull("days") ? arguments.path("days").asInt() : null;
        Integer minutes = arguments.hasNonNull("dailyMinutes") ? arguments.path("dailyMinutes").asInt() : null;
        String level = arguments.path("level").asText("BEGINNER");
        PlanDetail detail = planService.generate(context.userId(), goal, level, days, minutes);

        String firstDay = detail.days().isEmpty() ? "" : detail.days().get(0).tasks().stream()
                .map(t -> "「" + t.title() + "」")
                .limit(3)
                .reduce((a, b) -> a + "、" + b)
                .orElse("");
        String content = "已创建学习计划《%s》，共 %d 天、%d 个任务，总进度 %d%%。首日任务：%s。"
                .formatted(detail.plan().title(), detail.days().size(), detail.plan().totalTasks(),
                        detail.plan().progress(), firstDay);

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("kind", "study_plan");
        artifact.put("planId", detail.plan().id());
        artifact.put("title", detail.plan().title());
        artifact.put("durationDays", detail.plan().durationDays());
        artifact.put("totalTasks", detail.plan().totalTasks());
        artifact.put("summary", detail.plan().summary());
        return ToolResult.of(content, "已生成学习计划《" + detail.plan().title() + "》", artifact, List.of());
    }
}
