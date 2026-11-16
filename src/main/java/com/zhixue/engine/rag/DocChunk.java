package com.zhixue.engine.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "zx_doc_chunks")
public class DocChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", nullable = false)
    private Long documentId;

    @Column(nullable = false)
    private int seq;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "char_count", nullable = false)
    private int charCount;

    @Column(name = "token_count", nullable = false)
    private int tokenCount;

    @Column(name = "sparse_vector", nullable = false, columnDefinition = "MEDIUMTEXT")
    private String sparseVector;

    private String keywords;

    public static DocChunk of(Long documentId, int seq, String content, String sparseVector, int tokenCount,
                              String keywords) {
        DocChunk chunk = new DocChunk();
        chunk.documentId = documentId;
        chunk.seq = seq;
        chunk.content = content;
        chunk.charCount = content.length();
        chunk.sparseVector = sparseVector;
        chunk.tokenCount = tokenCount;
        chunk.keywords = keywords;
        return chunk;
    }

    public Long getId() {
        return id;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public int getSeq() {
        return seq;
    }

    public String getContent() {
        return content;
    }

    public int getCharCount() {
        return charCount;
    }

    public int getTokenCount() {
        return tokenCount;
    }

    public String getSparseVector() {
        return sparseVector;
    }

    public String getKeywords() {
        return keywords;
    }
}
