package com.zhixue.engine.rag;

/** 检索时使用的切片投影，避免 N+1 查询文档表。 */
public record ChunkRow(
        Long chunkId,
        Long documentId,
        String documentTitle,
        int seq,
        String content,
        String sparseVector,
        int tokenCount
) {
}
