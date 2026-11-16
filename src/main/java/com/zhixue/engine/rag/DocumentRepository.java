package com.zhixue.engine.rag;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByUserIdOrderByIdDesc(Long userId);

    List<Document> findByUserIdIsNullOrderByIdAsc();

    Optional<Document> findFirstByUserIdIsNullAndTitle(String title);

    @Query("select d from Document d where d.userId = :userId or d.userId is null order by d.id desc")
    List<Document> findAccessible(@Param("userId") long userId);

    long countByUserId(long userId);
}
