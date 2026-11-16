package com.zhixue.engine.analytics;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningEventService {

    private final LearningEventRepository repository;

    public LearningEventService(LearningEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(long userId, String type, String refType, Long refId, String knowledgePoint,
                       Double score, int minutes, String detail) {
        repository.save(LearningEvent.of(userId, type)
                .ref(refType, refId)
                .point(knowledgePoint)
                .score(score)
                .minutes(minutes)
                .detail(detail));
    }
}
