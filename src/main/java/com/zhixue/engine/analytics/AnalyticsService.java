package com.zhixue.engine.analytics;

import com.zhixue.engine.analytics.AnalyticsDtos.Activity;
import com.zhixue.engine.analytics.AnalyticsDtos.AiStats;
import com.zhixue.engine.analytics.AnalyticsDtos.AiUsageItem;
import com.zhixue.engine.analytics.AnalyticsDtos.DayActivity;
import com.zhixue.engine.analytics.AnalyticsDtos.MasteryPoint;
import com.zhixue.engine.analytics.AnalyticsDtos.Overview;
import com.zhixue.engine.analytics.AnalyticsDtos.Stats;
import com.zhixue.engine.quiz.QuizRepository;
import com.zhixue.engine.rag.Document;
import com.zhixue.engine.rag.DocumentRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AnalyticsService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MM-dd");
    private static final String[] WEEK_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};

    private final LearningEventRepository events;
    private final AiUsageLogRepository aiLogs;
    private final QuizRepository quizzes;
    private final DocumentRepository documents;

    public AnalyticsService(LearningEventRepository events, AiUsageLogRepository aiLogs,
                            QuizRepository quizzes, DocumentRepository documents) {
        this.events = events;
        this.aiLogs = aiLogs;
        this.quizzes = quizzes;
        this.documents = documents;
    }

    public Overview overview(long userId) {
        LocalDate today = LocalDate.now();
        List<LearningEvent> recent = events.findByUserIdAndCreatedAtAfterOrderByCreatedAtDesc(
                userId, today.minusDays(59).atStartOfDay());

        Map<LocalDate, int[]> byDay = new HashMap<>();
        for (LearningEvent event : recent) {
            LocalDate day = event.getCreatedAt().toLocalDate();
            int[] bucket = byDay.computeIfAbsent(day, key -> new int[2]);
            bucket[0] += event.getMinutes();
            bucket[1] += 1;
        }

        List<DayActivity> weekly = new ArrayList<>();
        for (int i = 6; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            int[] bucket = byDay.getOrDefault(day, new int[2]);
            weekly.add(new DayActivity(day.toString(), WEEK_LABELS[day.getDayOfWeek().getValue() - 1],
                    bucket[0], bucket[1]));
        }

        int streak = 0;
        LocalDate cursor = byDay.containsKey(today) ? today : today.minusDays(1);
        while (byDay.containsKey(cursor)) {
            streak++;
            cursor = cursor.minusDays(1);
        }

        LocalDate since30 = today.minusDays(29);
        int minutes30 = 0;
        Set<LocalDate> active30 = new HashSet<>();
        for (LearningEvent event : recent) {
            LocalDate day = event.getCreatedAt().toLocalDate();
            if (!day.isBefore(since30)) {
                minutes30 += event.getMinutes();
                active30.add(day);
            }
        }

        List<MasteryPoint> mastery = new ArrayList<>();
        for (Object[] row : events.masteryByKnowledgePoint(userId)) {
            String point = (String) row[0];
            double avg = row[1] == null ? 0 : ((Number) row[1]).doubleValue();
            int attempts = row[2] == null ? 0 : ((Number) row[2]).intValue();
            mastery.add(new MasteryPoint(point, (int) Math.round(avg), attempts));
        }
        mastery.sort(Comparator.comparingInt(MasteryPoint::attempts).reversed());
        List<MasteryPoint> topMastery = mastery.size() > 8 ? new ArrayList<>(mastery.subList(0, 8)) : mastery;
        List<MasteryPoint> weak = mastery.stream()
                .filter(point -> point.mastery() < 75)
                .sorted(Comparator.comparingInt(MasteryPoint::mastery))
                .limit(4)
                .toList();

        long quizCount = quizzes.countCompleted(userId);
        Double avgScore = quizzes.averageScore(userId);
        List<Document> docs = documents.findAccessible(userId);
        int chunkTotal = docs.stream().mapToInt(Document::getChunkCount).sum();

        AiUsageLogRepository.AiSummary aiSummary = aiLogs.summary(userId);
        long aiCalls = aiSummary == null || aiSummary.getCalls() == null ? 0 : aiSummary.getCalls();
        long promptTokens = aiSummary == null || aiSummary.getPromptTokens() == null ? 0 : aiSummary.getPromptTokens();
        long completionTokens = aiSummary == null || aiSummary.getCompletionTokens() == null
                ? 0 : aiSummary.getCompletionTokens();
        long blocked = aiSummary == null || aiSummary.getBlocked() == null ? 0 : aiSummary.getBlocked();

        Stats stats = new Stats(minutes30, active30.size(), streak, quizCount,
                avgScore == null ? 0 : (int) Math.round(avgScore), docs.size(), chunkTotal, aiCalls);

        List<Activity> activities = events.findTop20ByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(event -> !"QUIZ_ITEM".equals(event.getType()))
                .limit(8)
                .map(event -> new Activity(event.getType(), titleOf(event.getType()), event.getDetail(),
                        event.getCreatedAt()))
                .toList();

        String insight = buildInsight(stats, weak, weekly);
        return new Overview(stats, weekly, topMastery, weak, activities,
                new AiStats(aiCalls, promptTokens + completionTokens, blocked), insight);
    }

    public List<AiUsageItem> aiUsage(long userId) {
        return aiLogs.findTop25ByUserIdOrderByIdDesc(userId).stream().map(AiUsageItem::of).toList();
    }

    private String titleOf(String type) {
        return switch (type) {
            case "QUIZ" -> "完成了一次 AI 测验";
            case "QUIZ_ITEM" -> "完成知识点练习";
            case "PLAN_TASK" -> "打卡学习任务";
            case "CHAT" -> "与 AI 导师对话";
            case "KNOWLEDGE" -> "更新知识库";
            default -> "学习记录了新进展";
        };
    }

    private String buildInsight(Stats stats, List<MasteryPoint> weak, List<DayActivity> weekly) {
        if (stats.streakDays() == 0 && stats.quizzes() == 0) {
            return "还没有学习记录。建议从「AI 导师」提一个具体问题开始，AI 会据此生成你的学习画像。";
        }
        if (!weak.isEmpty()) {
            MasteryPoint worst = weak.get(0);
            return "「%s」的掌握度只有 %d%%，是本阶段最值得投入的地方。建议先让 AI 导师用类比讲一遍，再做 5 道同类题巩固。"
                    .formatted(worst.point(), worst.mastery());
        }
        int weeklyMinutes = weekly.stream().mapToInt(DayActivity::minutes).sum();
        if (weeklyMinutes < 60) {
            return "本周累计学习 %d 分钟。保持每天 25 分钟的节奏，比周末集中突击更容易形成长期记忆。"
                    .formatted(weeklyMinutes);
        }
        return "本周已累计学习 %d 分钟，节奏不错。可以尝试把难度提高一档，或用「AI 出题」检验是否真的掌握。"
                .formatted(weeklyMinutes);
    }
}
