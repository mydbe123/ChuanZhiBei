package com.zhixue.engine.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.agent.AgentTool;
import com.zhixue.engine.agent.ToolContext;
import com.zhixue.engine.agent.ToolResult;
import com.zhixue.engine.quiz.QuizDtos.QuizDetail;
import com.zhixue.engine.quiz.QuizService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QuizTool implements AgentTool {

    private final QuizService quizService;

    public QuizTool(QuizService quizService) {
        this.quizService = quizService;
    }

    @Override
    public String name() {
        return "generate_quiz";
    }

    @Override
    public String description() {
        return "针对某个主题生成一组 AI 单选题并保存，学生可在「智能测验」中作答并获得批改与学情诊断。"
                + "当学生想检验掌握程度、要求出题或想练习时调用。";
    }

    @Override
    public Map<String, Object> parameters() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("topic", Map.of("type", "string", "description", "测验主题或知识点"));
        properties.put("count", Map.of("type", "integer", "description", "题目数量，3-10，默认 5"));
        properties.put("difficulty", Map.of("type", "string", "enum", List.of("EASY", "MEDIUM", "HARD"),
                "description", "难度"));
        return Map.of("type", "object", "properties", properties, "required", List.of("topic"));
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String topic = arguments.path("topic").asText("数据结构与算法");
        Integer count = arguments.hasNonNull("count") ? arguments.path("count").asInt() : null;
        String difficulty = arguments.path("difficulty").asText("MEDIUM");
        QuizDetail quiz = quizService.generate(context.userId(), topic, difficulty, count);

        String content = "已生成测验《%s》，共 %d 道单选题。学生可以点击卡片进入作答，提交后会得到逐题解析与 AI 学情诊断。"
                .formatted(quiz.title(), quiz.questionCount());
        Map<String, Object> artifact = new LinkedHashMap<>();
        artifact.put("kind", "quiz");
        artifact.put("quizId", quiz.id());
        artifact.put("title", quiz.title());
        artifact.put("questionCount", quiz.questionCount());
        artifact.put("difficulty", quiz.difficulty());
        return ToolResult.of(content, "已生成测验《" + quiz.title() + "》，共 " + quiz.questionCount() + " 题",
                artifact, List.of());
    }
}
