package com.zhixue.engine.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 句感知的文本切片器：优先在句末标点处断开，并保留重叠窗口，避免语义被硬切断。
 */
@Component
public class TextSplitter {

    private static final String SENTENCE_BOUNDARY = "(?<=[。！？!?；;\\n])";

    public List<String> split(String text, int chunkSize, int overlap) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String cleaned = text.replace("\r\n", "\n")
                .replaceAll("\n{3,}", "\n\n")
                .replaceAll("[ \\t]{2,}", " ")
                .trim();

        List<String> units = splitSentences(cleaned, chunkSize);
        StringBuilder current = new StringBuilder();
        for (String unit : units) {
            if (current.length() > 0 && current.length() + unit.length() > chunkSize) {
                chunks.add(current.toString().trim());
                String tail = tailOf(current.toString(), overlap);
                current.setLength(0);
                current.append(tail);
            }
            current.append(unit);
        }
        if (!current.toString().isBlank()) {
            chunks.add(current.toString().trim());
        }
        return chunks.stream().filter(c -> c.length() > 20).toList();
    }

    private List<String> splitSentences(String text, int chunkSize) {
        List<String> units = new ArrayList<>();
        for (String sentence : text.split(SENTENCE_BOUNDARY)) {
            if (sentence.length() <= chunkSize) {
                units.add(sentence);
            } else {
                for (int i = 0; i < sentence.length(); i += chunkSize) {
                    units.add(sentence.substring(i, Math.min(sentence.length(), i + chunkSize)));
                }
            }
        }
        return units;
    }

    private String tailOf(String text, int overlap) {
        if (overlap <= 0 || text.length() <= overlap) {
            return text.length() > overlap ? text.substring(text.length() - overlap) : "";
        }
        return text.substring(text.length() - overlap);
    }
}
