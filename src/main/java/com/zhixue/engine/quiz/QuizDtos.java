package com.zhixue.engine.quiz;

import java.time.LocalDateTime;
import java.util.List;

public final class QuizDtos {

    private QuizDtos() {
    }

    public record Option(String key, String text) {
    }

    public record QuestionView(long id, String type, String stem, List<Option> options, String knowledgePoint) {
    }

    public record QuizSummary(long id, String title, String topic, String difficulty, int questionCount,
                              Integer score, String status, LocalDateTime createdAt) {
        public static QuizSummary of(Quiz quiz) {
            return new QuizSummary(quiz.getId(), quiz.getTitle(), quiz.getTopic(), quiz.getDifficulty(),
                    quiz.getQuestionCount(), quiz.getScore(), quiz.getStatus(), quiz.getCreatedAt());
        }
    }

    public record QuizDetail(long id, String title, String topic, String difficulty, String status,
                             Integer score, int questionCount, LocalDateTime createdAt,
                             List<QuestionView> questions) {
    }

    public record AnswerInput(long questionId, String answer) {
    }

    public record SubmitRequest(List<AnswerInput> answers) {
    }

    public record QuestionResult(long questionId, String stem, List<Option> options, String correctAnswer,
                                 String userAnswer, boolean correct, String explanation, String knowledgePoint) {
    }

    public record SubmitResult(long quizId, String title, int score, int total, int correctCount,
                               List<QuestionResult> results, List<String> weakPoints, String diagnosis) {
    }

    public record GenerateRequest(String topic, String difficulty, Integer count) {
    }
}
