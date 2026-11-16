package com.zhixue.engine.rag;

import java.time.LocalDateTime;
import java.util.List;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record DocumentView(
            Long id,
            String title,
            String sourceType,
            String fileName,
            int byteSize,
            int charCount,
            int chunkCount,
            String summary,
            LocalDateTime createdAt,
            boolean shared
    ) {
        public static DocumentView of(Document document) {
            return new DocumentView(document.getId(), document.getTitle(), document.getSourceType(),
                    document.getFileName(), document.getByteSize(), document.getCharCount(),
                    document.getChunkCount(), document.getSummary(), document.getCreatedAt(),
                    document.getUserId() == null);
        }
    }

    public record Overview(List<DocumentView> documents, int documentCount, int chunkCount, int charCount) {
    }

    public record TextIngestRequest(String title, String content) {
    }

    public record SearchRequest(String query, Integer topK) {
    }

    public record Citation(String documentTitle, int seq, String snippet, double score) {
        public static Citation of(RetrievalHit hit) {
            return new Citation(hit.documentTitle(), hit.seq(), hit.snippet(), hit.score());
        }
    }
}
