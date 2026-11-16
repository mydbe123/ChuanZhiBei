package com.zhixue.engine.rag;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TokenizerTest {

    private final Tokenizer tokenizer = new Tokenizer();

    @Test
    void producesBigramsAndUnigramsForChinese() {
        Map<String, Integer> tf = tokenizer.termFrequencies("二叉树遍历");

        assertThat(tf).containsKeys("b:二叉", "b:叉树", "b:树遍", "b:遍历");
        assertThat(tf.get("b:二叉")).isEqualTo(1);
    }

    @Test
    void dropsStopwordsButKeepsContentWords() {
        List<String> tokens = tokenizer.tokenize("我 的 进程 调度");

        assertThat(tokens).doesNotContain("u:我", "u:的", "b:我的");
        assertThat(tokens).contains("b:进程", "b:调度");
    }

    @Test
    void weightsBigramHigherThanUnigram() {
        Map<String, Double> vector = tokenizer.toWeightedVector(tokenizer.termFrequencies("图"));

        assertThat(vector.get("u:图")).isNotNull();
        Map<String, Double> bigramVector = tokenizer.toWeightedVector(tokenizer.termFrequencies("二叉树"));

        assertThat(bigramVector.get("b:二叉")).isGreaterThan(bigramVector.get("u:二"));
    }

    @Test
    void handlesMixedChineseAndEnglish() {
        Map<String, Integer> tf = tokenizer.termFrequencies("MySQL 索引 B+Tree");

        assertThat(tf).containsKeys("w:mysql", "b:索引");
    }
}
