package com.zhixue.engine.agent.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.agent.AgentTool;
import com.zhixue.engine.agent.ToolContext;
import com.zhixue.engine.agent.ToolResult;
import com.zhixue.engine.rag.KnowledgeService;
import com.zhixue.engine.rag.RetrievalHit;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RAG 检索工具：把知识库原文片段回填给模型，并附带可追溯引用。 */
@Component
public class KnowledgeSearchTool implements AgentTool {

    private final KnowledgeService knowledgeService;

    public KnowledgeSearchTool(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @Override
    public String name() {
        return "search_knowledge_base";
    }

    @Override
    public String description() {
        return "检索学生的知识库（含公共课程资料与学生上传的文档），返回带出处的原文片段。"
                + "当问题涉及课程事实、笔记内容、需要引用来源，或学生要求"
                + "「根据我的资料回答」时，必须先调用本工具。";
    }

    @Override
    public Map<String, Object> parameters() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "query", Map.of("type", "string", "description", "检索语句或关键词，尽量具体"),
                        "topK", Map.of("type", "integer", "description", "返回片段数量，1-8，默认 5")),
                "required", List.of("query"));
    }

    @Override
    public ToolResult execute(JsonNode arguments, ToolContext context) {
        String query = arguments.path("query").asText("");
        int topK = arguments.path("topK").asInt(5);
        List<RetrievalHit> hits = knowledgeService.search(context.userId(), query, topK);
        if (hits.isEmpty()) {
            return ToolResult.text("知识库中没有检索到与「" + query + "」相关的资料。"
                    + "请改为基于学科通用知识回答，并明确说明这不是来自学生上传的资料。");
        }
        StringBuilder content = new StringBuilder("检索到 " + hits.size() + " 个相关片段：\n\n");
        List<Map<String, Object>> citations = new ArrayList<>();
        int index = 1;
        for (RetrievalHit hit : hits) {
            content.append("[资料").append(index).append("] 《").append(hit.documentTitle())
                    .append("》第 ").append(hit.seq() + 1).append(" 段（相关度 ")
                    .append(String.format("%.2f", hit.score())).append("）\n")
                    .append(hit.content()).append("\n\n");

            Map<String, Object> citation = new LinkedHashMap<>();
            citation.put("index", index);
            citation.put("documentTitle", hit.documentTitle());
            citation.put("seq", hit.seq() + 1);
            citation.put("snippet", hit.snippet());
            citation.put("score", hit.score());
            citations.add(citation);
            index++;
        }
        String summary = "检索到 %d 个片段，来自《%s》等资料".formatted(hits.size(), hits.get(0).documentTitle());
        return ToolResult.of(content.toString(), summary,
                Map.of("citations", citations, "hitCount", hits.size()), citations);
    }
}
