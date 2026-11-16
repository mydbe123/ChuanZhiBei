package com.zhixue.engine.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextSplitterTest {

    private final TextSplitter splitter = new TextSplitter();

    @Test
    void splitsLongTextIntoBoundedChunks() {
        String text = "进程是资源分配的基本单位。线程是CPU调度的基本单位。".repeat(12);

        List<String> chunks = splitter.split(text, 120, 20);

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.length()).isLessThanOrEqualTo(140));
    }

    @Test
    void keepsOverlapBetweenAdjacentChunks() {
        String text = "第一句话讲进程与线程的区别。第二句话讲调度算法的时间片。第三句话讲死锁的四个必要条件。";

        List<String> chunks = splitter.split(text, 30, 12);

        assertThat(chunks).hasSizeGreaterThan(1);
        String tail = chunks.get(0).substring(chunks.get(0).length() - 8);
        assertThat(chunks.get(1)).contains(tail);
    }

    @Test
    void ignoresBlankInput() {
        assertThat(splitter.split("   ", 100, 10)).isEmpty();
        assertThat(splitter.split(null, 100, 10)).isEmpty();
    }
}
