package com.zhixue.engine.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中英文混合分词器。
 * <p>
 * 中文按「单字 + 相邻双字（bigram）」切分，英文/数字按词切分。
 * 这样无需引入外部分词词典即可获得稳定的检索召回，同时通过前缀区分特征类型，
 * 便于在向量化时给双字更高的权重（bigram 语义强于单字）。
 */
@Component
public class Tokenizer {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("([\\u4e00-\\u9fa5]+)|([A-Za-z][A-Za-z0-9_+#.]{1,})");

    private static final Set<String> STOPWORDS = Set.of(
            "的", "了", "和", "是", "在", "我", "有", "就", "不", "人", "都", "一", "上", "也", "很",
            "到", "说", "要", "去", "你", "会", "着", "看", "好", "这", "那", "中", "为", "对", "以",
            "并", "从", "但", "而", "则", "其", "之", "于", "此", "即", "可", "该", "把", "被", "与",
            "及", "或", "等", "如", "若", "且", "让", "请", "帮", "吗", "呢", "吧", "啊", "么",
            "the", "a", "an", "of", "to", "is", "are", "and", "or", "in", "on", "for", "with", "as",
            "by", "at", "be", "this", "that", "it", "we", "you", "they", "from", "not", "can"
    );

    /** 单字特征相对双字的权重 */
    private static final double UNIGRAM_WEIGHT = 0.35;
    private static final double BIGRAM_WEIGHT = 1.0;
    private static final double WORD_WEIGHT = 1.0;

    public List<String> tokenize(String text) {
        return new ArrayList<>(termFrequencies(text).keySet());
    }

    public Map<String, Integer> termFrequencies(String text) {
        Map<String, Integer> tf = new LinkedHashMap<>();
        if (text == null || text.isBlank()) {
            return tf;
        }
        Matcher matcher = TOKEN_PATTERN.matcher(normalize(text));
        while (matcher.find()) {
            String cjk = matcher.group(1);
            if (cjk != null) {
                for (int i = 0; i < cjk.length(); i++) {
                    String unigram = String.valueOf(cjk.charAt(i));
                    if (!STOPWORDS.contains(unigram)) {
                        tf.merge("u:" + unigram, 1, Integer::sum);
                    }
                    if (i + 1 < cjk.length()) {
                        tf.merge("b:" + cjk.substring(i, i + 2), 1, Integer::sum);
                    }
                }
            } else {
                String word = matcher.group(2).toLowerCase();
                if (!STOPWORDS.contains(word)) {
                    tf.merge("w:" + word, 1, Integer::sum);
                }
            }
        }
        return tf;
    }

    /** 词频 -> 亚线性加权稀疏向量（未做 IDF，IDF 在检索时按语料统计） */
    public Map<String, Double> toWeightedVector(Map<String, Integer> tf) {
        Map<String, Double> vector = new LinkedHashMap<>();
        tf.forEach((term, count) -> vector.put(term, (1 + Math.log(count)) * featureWeight(term)));
        return vector;
    }

    private double featureWeight(String term) {
        if (term.startsWith("u:")) {
            return UNIGRAM_WEIGHT;
        }
        if (term.startsWith("w:")) {
            return WORD_WEIGHT;
        }
        return BIGRAM_WEIGHT;
    }

    private String normalize(String text) {
        return text.toLowerCase().replaceAll("[\\p{Punct}&&[^+#.]]", " ");
    }
}
