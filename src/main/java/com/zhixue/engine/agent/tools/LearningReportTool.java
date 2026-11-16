package com.zhixue.engine.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.agent.AgentTool;
import com.zhixue.engine.agent.ToolContext;
import com.zhixue.engine.agent.ToolResult;
import com.zhixue.engine.analytics.AnalyticsDtos.MasteryPoint;
import com.zhixue.engine.analytics.AnalyticsDtos.Overview;
import com.zhixue.engine.analytics.AnalyticsService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class LearningReportTool implements AgentTool {

    private final AnalyticsService analyticsService;

    public LearningReportTool(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @Override
    public String name() {
        return "get_learning_report";
    }

    @Override
    public String description() {
        return "读取学生近期的学习数据（学习时长、测验成绩、知识点掌握度、AI 使用量）。"
                + "当学生问「我最近学得怎么样」「我哪里薄弱」「下一步该做什么」时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of("type", "object", "properties", Map.of(), "required", List.of());
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        Overview overview = analyticsService.overview(context.userId());
        String mastery = overview.mastery().isEmpty() ? "暂无"
                : overview.mastery().stream()
                .map(p -> p.point() + " " + p.mastery() + "%")
                .collect(Collectors.joining("、"));
        String weak = overview.weakPoints().isEmpty() ? "暂无明显薄弱点"
                : overview.weakPoints().stream().map(MasteryPoint::point).collect(Collectors.joining("、"));

        String content = """
                近 30 天学习数据：
                - 累计学习时长：%d 分钟，活跃 %d 天，连续打卡 %d 天
                - AI 测验：%d 次，平均分 %d
                - 知识点掌握度：%s
                - 薄弱知识点：%s
                - 系统洞察：%s
                """.formatted(overview.stats().studyMinutes(), overview.stats().activeDays(),
                overview.stats().streakDays(), overview.stats().quizzes(), overview.stats().avgScore(),
                mastery, weak, overview.insight());

        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("kind", "learning_report");
        artifact.put("title", "近 30 天学习报告");
        artifact.put("stats", overview.stats());
        List<Map<String, Object>> weakList = new ArrayList<>();
        overview.weakPoints().forEach(p -> weakList.add(Map.of("point", p.point(), "mastery", p.mastery())));
        artifact.put("weakPoints", weakList);
        artifact.put("insight", overview.insight());
        return ToolResult.of(content, "已读取学习报告：近 30 天 " + overview.stats().studyMinutes() + " 分钟",
                artifact, List.of());
    }
}
