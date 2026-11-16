package com.zhixue.engine.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * JSON 辅助工具。大模型输出经常夹带 markdown 代码块或前后缀说明，这里提供容错解析。
 */
@Component
public class Json {

    private final ObjectMapper mapper;

    public Json(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public ObjectMapper mapper() {
        return mapper;
    }

    public ObjectNode obj() {
        return mapper.createObjectNode();
    }

    public ArrayNode arr() {
        return mapper.createArrayNode();
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new BusinessException(500, "JSON 序列化失败: " + e.getMessage());
        }
    }

    public JsonNode read(String json) {
        try {
            return mapper.readTree(json);
        } catch (Exception e) {
            throw new BusinessException(500, "JSON 解析失败: " + e.getMessage());
        }
    }

    public <T> T read(String json, TypeReference<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new BusinessException(500, "JSON 解析失败: " + e.getMessage());
        }
    }

    public <T> T convert(Object value, Class<T> type) {
        return mapper.convertValue(value, type);
    }

    public Map<String, Object> toMap(Object value) {
        return mapper.convertValue(value, new TypeReference<Map<String, Object>>() {
        });
    }

    public List<Map<String, Object>> toMapList(String json) {
        if (json == null || json.isBlank()) {
            return new ArrayList<>();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * 从可能包含 markdown 围栏、解说文字的文本中提取第一个完整 JSON 对象/数组。
     */
    public JsonNode extractJson(String raw) {
        if (raw == null) {
            throw new BusinessException(500, "模型未返回内容");
        }
        String text = raw.trim();
        int fence = text.indexOf("```");
        if (fence >= 0) {
            int start = text.indexOf('\n', fence);
            int end = text.indexOf("```", start + 1);
            if (start > 0 && end > start) {
                text = text.substring(start + 1, end).trim();
            }
        }
        try {
            return mapper.readTree(text);
        } catch (Exception ignored) {
            // fall through to brace scanning
        }
        int objStart = text.indexOf('{');
        int arrStart = text.indexOf('[');
        int start = objStart < 0 ? arrStart : (arrStart < 0 ? objStart : Math.min(objStart, arrStart));
        if (start < 0) {
            throw new BusinessException(500, "模型输出中未找到 JSON 结构");
        }
        char open = text.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
            } else if (c == open) {
                depth++;
            } else if (c == close) {
                depth--;
                if (depth == 0) {
                    try {
                        return mapper.readTree(text.substring(start, i + 1));
                    } catch (Exception e) {
                        throw new BusinessException(500, "模型返回的 JSON 无法解析");
                    }
                }
            }
        }
        throw new BusinessException(500, "模型返回的 JSON 不完整");
    }
}
