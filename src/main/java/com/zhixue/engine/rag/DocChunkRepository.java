package com.zhixue.engine.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface DocChunkRepository extends JpaRepository<DocChunk, Long> {

    List<DocChunk> findByDocumentIdOrderBySeqAsc(Long documentId);

    @Modifying
    @Transactional
    void deleteByDocumentId(Long documentId);

    /**
     * 取出当前用户可见的所有切片：自己的文档 + 公共示例知识库。
     */
    @Query("""
            select new com.zhixue.engine.rag.ChunkRow(
                c.id, c.documentId, d.title, c.seq, c.content, c.sparseVector, c.tokenCount)
            from DocChunk c, Document d
            where c.documentId = d.id and (d.userId = :userId or d.userId is null)
            """)
    List<ChunkRow> findAccessibleChunks(@Param("userId") long userId);
}
