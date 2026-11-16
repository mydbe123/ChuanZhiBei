package com.zhixue.engine.quiz;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, Long> {

    List<Quiz> findByUserIdOrderByIdDesc(long userId);

    long countByUserId(long userId);

    @Query("select avg(q.score) from Quiz q where q.userId = :userId and q.score is not null")
    Double averageScore(@Param("userId") long userId);

    @Query("select count(q) from Quiz q where q.userId = :userId and q.score is not null")
    long countCompleted(@Param("userId") long userId);
}
