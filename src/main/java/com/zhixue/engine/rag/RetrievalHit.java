package com.zhixue.engine.rag;

/**
 * 检索命中结果，带上可追溯的引用信息（文档标题 + 切片序号）。
 */
public record RetrievalHit(
        long chunkId,
        long documentId,
        String documentTitle,
        int seq,
        String content,
        double score,
        double vectorScore,
        double keywordScore
) {
    public String snippet() {
        return content.length() > 260 ? content.substring(0, 260) + "…" : content;
    }
}
