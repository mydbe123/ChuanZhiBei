package com.zhixue.engine.demo;

import com.zhixue.engine.analytics.AiUsageLog;
import com.zhixue.engine.analytics.AiUsageLogRepository;
import com.zhixue.engine.analytics.LearningEvent;
import com.zhixue.engine.analytics.LearningEventRepository;
import com.zhixue.engine.chat.Conversation;
import com.zhixue.engine.chat.ConversationRepository;
import com.zhixue.engine.chat.Message;
import com.zhixue.engine.chat.MessageRepository;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.demo.DemoScript.SeedQuestion;
import com.zhixue.engine.plan.StudyPlan;
import com.zhixue.engine.plan.StudyPlanRepository;
import com.zhixue.engine.plan.StudyTask;
import com.zhixue.engine.plan.StudyTaskRepository;
import com.zhixue.engine.quiz.Quiz;
import com.zhixue.engine.quiz.QuizQuestion;
import com.zhixue.engine.quiz.QuizQuestionRepository;
import com.zhixue.engine.quiz.QuizRepository;
import com.zhixue.engine.rag.KnowledgeDtos.DocumentView;
import com.zhixue.engine.rag.KnowledgeDtos.Overview;
import com.zhixue.engine.rag.KnowledgeService;
import com.zhixue.engine.user.AuthService;
import com.zhixue.engine.user.User;
import com.zhixue.engine.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 演示数据导入：为体验账号准备一条完整的「学习故事」。
 * <p>
 * 包含个人笔记、10 天学习计划（含打卡进度）、3 次测验（2 次已完成 + 1 次待作答）、
 * 3 段对话（含思考过程、工具轨迹与引用）以及 12 天的学习/调用记录，
 * 让评审第一次打开就能看到完整的产品形态。
 * <p>
 * 幂等：仅当体验账号尚无测验记录时才导入；可通过 {@code app.demo.seed=false} 关闭。
 */
@Component
@Order(2)
public class DemoStorySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoStorySeeder.class);

    private final boolean enabled;
    private final AuthService authService;
    private final UserRepository users;
    private final KnowledgeService knowledgeService;
    private final StudyPlanRepository plans;
    private final StudyTaskRepository tasks;
    private final QuizRepository quizzes;
    private final QuizQuestionRepository questions;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final LearningEventRepository events;
    private final AiUsageLogRepository aiLogs;
    private final Json json;

    public DemoStorySeeder(@Value("${app.demo.seed:true}") boolean enabled,
                           AuthService authService, UserRepository users, KnowledgeService knowledgeService,
                           StudyPlanRepository plans, StudyTaskRepository tasks, QuizRepository quizzes,
                           QuizQuestionRepository questions, ConversationRepository conversations,
                           MessageRepository messages, LearningEventRepository events,
                           AiUsageLogRepository aiLogs, Json json) {
        this.enabled = enabled;
        this.authService = authService;
        this.users = users;
        this.knowledgeService = knowledgeService;
        this.plans = plans;
        this.tasks = tasks;
        this.quizzes = quizzes;
        this.questions = questions;
        this.conversations = conversations;
        this.messages = messages;
        this.events = events;
        this.aiLogs = aiLogs;
        this.json = json;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        try {
            seed();
        } catch (Exception e) {
            log.warn("演示数据导入失败（不影响主流程）: {}", e.getMessage(), e);
        }
    }

    private void seed() {
        User demo = ensureDemoUser();
        long userId = demo.getId();
        if (quizzes.countByUserId(userId) > 0) {
            log.info("体验账号已有学习数据，跳过演示数据导入");
            return;
        }

        LocalDate today = LocalDate.now();

        DocumentView note = knowledgeService.ingestText(userId, DemoScript.NOTE_TITLE, DemoScript.NOTE_CONTENT);
        StudyPlan plan = seedPlan(userId, today);
        Quiz quiz1 = seedQuiz(userId, today.minusDays(6), DemoScript.QUIZ1_TITLE, DemoScript.QUIZ1_TOPIC,
                "MEDIUM", DemoScript.QUIZ1, true);
        Quiz quiz2 = seedQuiz(userId, today.minusDays(3), DemoScript.QUIZ2_TITLE, DemoScript.QUIZ2_TOPIC,
                "HARD", DemoScript.QUIZ2, true);
        seedQuiz(userId, today, DemoScript.QUIZ3_TITLE, DemoScript.QUIZ3_TOPIC, "MEDIUM",
                DemoScript.QUIZ3, false);

        Overview overview = knowledgeService.overview(userId);
        seedConversations(userId, today, plan, note, overview);
        seedEvents(userId, today);
        seedAiLogs(userId, today);

        log.info("演示数据已就绪：计划 1 个 / 测验 3 次 / 对话 3 段 / 学习记录 12 天");
    }

    private User ensureDemoUser() {
        User demo = users.findByUsername(AuthService.DEMO_USERNAME).orElse(null);
        if (demo == null) {
            authService.demoLogin();
            demo = users.findByUsername(AuthService.DEMO_USERNAME)
                    .orElseThrow(() -> new IllegalStateException("体验账号创建失败"));
        }
        return demo;
    }

    private StudyPlan seedPlan(long userId, LocalDate today) {
        StudyPlan plan = new StudyPlan();
        plan.setUserId(userId);
        plan.setTitle(DemoScript.PLAN_TITLE);
        plan.setGoal(DemoScript.PLAN_GOAL);
        plan.setLevel("INTERMEDIATE");
        plan.setDurationDays(10);
        plan.setDailyMinutes(60);
        plan.setSummary(DemoScript.PLAN_SUMMARY);
        plan.at(today.minusDays(5).atTime(20, 12));
        plans.save(plan);

        List<StudyTask> entities = new ArrayList<>();
        int[] seqPerDay = new int[11];
        for (Object[] row : DemoScript.PLAN_TASKS) {
            int day = (int) row[0];
            StudyTask task = new StudyTask();
            task.setPlanId(plan.getId());
            task.setDayIndex(day);
            task.setSeq(++seqPerDay[day]);
            task.setTitle((String) row[1]);
            task.setDetail((String) row[2]);
            task.setKnowledgePoints((String) row[3]);
            task.setMinutes((int) row[4]);
            boolean done = (boolean) row[5];
            task.setStatus(done ? "DONE" : "TODO");
            if (done) {
                // 第 1-4 天全部完成，第 5 天完成第一个任务，时间线分布在过去 5 天
                int daysAgo = Math.max(0, 5 - day);
                task.setCompletedAt(today.minusDays(daysAgo).atTime(19, 40));
            }
            entities.add(task);
        }
        tasks.saveAll(entities);
        return plan;
    }

    private Quiz seedQuiz(long userId, LocalDate date, String title, String topic, String difficulty,
                          List<SeedQuestion> seedQuestions, boolean completed) {
        Quiz quiz = new Quiz();
        quiz.setUserId(userId);
        quiz.setTitle(title);
        quiz.setTopic(topic);
        quiz.setDifficulty(difficulty);
        quiz.setQuestionCount(seedQuestions.size());
        // 今天的测验放在上午，避免出现「未来的时间」
        LocalDateTime created = date.equals(LocalDate.now()) ? date.atTime(8, 40) : date.atTime(20, 30);
        quiz.at(created);
        if (completed) {
            long correct = seedQuestions.stream().filter(SeedQuestion::correct).count();
            quiz.setScore((int) Math.round(correct * 100.0 / seedQuestions.size()));
            quiz.setStatus("DONE");
            quiz.setCompletedAt(created.plusMinutes(22));
        }
        quizzes.save(quiz);

        List<QuizQuestion> entities = new ArrayList<>();
        int seq = 1;
        for (SeedQuestion seed : seedQuestions) {
            QuizQuestion question = new QuizQuestion();
            question.setQuizId(quiz.getId());
            question.setSeq(seq++);
            question.setStem(seed.stem());
            question.setOptions(json.write(toOptions(seed.options())));
            question.setAnswer(seed.answer());
            question.setExplanation(seed.explanation());
            question.setKnowledgePoint(seed.point());
            question.setUserAnswer(seed.userAnswer());
            question.setCorrect(seed.userAnswer() == null ? null : seed.correct());
            entities.add(question);
        }
        questions.saveAll(entities);
        return quiz;
    }

    private List<Map<String, String>> toOptions(List<String> options) {
        List<Map<String, String>> result = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) {
            Map<String, String> option = new LinkedHashMap<>();
            option.put("key", String.valueOf((char) ('A' + i)));
            option.put("text", options.get(i));
            result.add(option);
        }
        return result;
    }

    private void seedConversations(long userId, LocalDate today, StudyPlan plan, DocumentView note,
                                   Overview overview) {
        LocalDateTime conv1Time = today.minusDays(6).atTime(20, 10);
        Conversation conv1 = conversation(userId, DemoScript.CONV1_TITLE, conv1Time, conv1Time.plusMinutes(9));
        conversations.save(conv1);
        messages.save(Message.of(conv1.getId(), "user", DemoScript.CONV1_USER)
                .at(conv1Time));
        messages.save(Message.of(conv1.getId(), "assistant", DemoScript.CONV1_ANSWER)
                .setReasoning(DemoScript.CONV1_REASONING)
                .setToolTrace(json.write(List.of(toolTrace("call_demo_001", "search_knowledge_base",
                        "{\"query\":\"二叉树 中序遍历 栈 非递归\"}",
                        "检索到 3 个片段，来自《" + note.title() + "》等资料",
                        Map.of("hitCount", 3)))))
                .setCitations(json.write(List.of(
                        citation(1, note.title(), 1,
                                "中序非递归遍历的模板：一路向左压栈，弹出即访问，再转向右子树。", 0.71),
                        citation(2, "数据结构与算法核心速查", 3,
                                "二叉树遍历：前序（根左右）、中序（左根右）、后序（左右根）、层序（BFS）。", 0.63))))
                .setTokens(1840)
                .at(conv1Time.plusMinutes(1)));

        LocalDateTime conv2Time = today.minusDays(2).atTime(21, 5);
        Conversation conv2 = conversation(userId, DemoScript.CONV2_TITLE, conv2Time, conv2Time.plusMinutes(2));
        conversations.save(conv2);
        messages.save(Message.of(conv2.getId(), "user", DemoScript.CONV2_USER).at(conv2Time));
        Map<String, Object> planArtifact = new LinkedHashMap<>();
        planArtifact.put("kind", "study_plan");
        planArtifact.put("planId", plan.getId());
        planArtifact.put("title", plan.getTitle());
        planArtifact.put("durationDays", plan.getDurationDays());
        planArtifact.put("totalTasks", DemoScript.PLAN_TASKS.length);
        planArtifact.put("summary", plan.getSummary());
        messages.save(Message.of(conv2.getId(), "assistant", DemoScript.CONV2_ANSWER)
                .setReasoning(DemoScript.CONV2_REASONING)
                .setToolTrace(json.write(List.of(toolTrace("call_demo_002", "create_study_plan",
                        "{\"goal\":\"10 天把树、图与最短路练到能独立做题\",\"days\":10,\"dailyMinutes\":60,"
                                + "\"level\":\"INTERMEDIATE\"}",
                        "已生成学习计划《" + plan.getTitle() + "》", planArtifact))))
                .setTokens(2240)
                .at(conv2Time.plusSeconds(48)));

        LocalDateTime conv3Time = today.atTime(9, 12);
        Conversation conv3 = conversation(userId, DemoScript.CONV3_TITLE, conv3Time, conv3Time.plusMinutes(1));
        conversations.save(conv3);
        messages.save(Message.of(conv3.getId(), "user", DemoScript.CONV3_USER).at(conv3Time));
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("studyMinutes", 550);
        stats.put("activeDays", 12);
        stats.put("streakDays", 12);
        stats.put("quizzes", 2);
        stats.put("avgScore", 78);
        stats.put("documents", overview.documentCount());
        stats.put("chunks", overview.chunkCount());
        stats.put("aiCalls", DemoScript.AI_LOGS.length);
        Map<String, Object> reportArtifact = new LinkedHashMap<>();
        reportArtifact.put("kind", "learning_report");
        reportArtifact.put("title", "近 30 天学习报告");
        reportArtifact.put("stats", stats);
        reportArtifact.put("weakPoints", List.of(
                Map.of("point", "非递归遍历", "mastery", 40),
                Map.of("point", "拓扑排序", "mastery", 40)));
        reportArtifact.put("insight", "「非递归遍历」的掌握度只有 40%，是本阶段最值得投入的地方。");
        messages.save(Message.of(conv3.getId(), "assistant", DemoScript.CONV3_ANSWER)
                .setReasoning(DemoScript.CONV3_REASONING)
                .setToolTrace(json.write(List.of(toolTrace("call_demo_003", "get_learning_report", "{}",
                        "已读取学习报告：近 30 天 550 分钟", reportArtifact))))
                .setTokens(1680)
                .at(conv3Time.plusSeconds(26)));
    }

    private Conversation conversation(long userId, String title, LocalDateTime created, LocalDateTime updated) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setScene("TUTOR");
        conversation.setMessageCount(2);
        conversation.at(created, updated);
        return conversation;
    }

    private Map<String, Object> toolTrace(String id, String name, String arguments, String summary,
                                          Map<String, Object> artifact) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", id);
        item.put("name", name);
        item.put("arguments", arguments);
        item.put("status", "success");
        item.put("summary", summary);
        item.put("artifact", artifact);
        return item;
    }

    private Map<String, Object> citation(int index, String title, int seq, String snippet, double score) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("index", index);
        item.put("documentTitle", title);
        item.put("seq", seq);
        item.put("snippet", snippet);
        item.put("score", score);
        return item;
    }

    private void seedEvents(long userId, LocalDate today) {
        List<LearningEvent> entities = new ArrayList<>();
        for (int i = 0; i < DemoScript.DAILY_MINUTES.length; i++) {
            LocalDate day = today.minusDays(DemoScript.DAILY_MINUTES.length - 1 - i);
            entities.add(LearningEvent.of(userId, DemoScript.DAILY_TYPES[i])
                    .minutes(DemoScript.DAILY_MINUTES[i])
                    .detail(DemoScript.DAILY_DETAIL[i])
                    .at(day.atTime(19, 20)));
        }

        // 两次已完成测验的知识点表现 —— 驱动掌握度画像与薄弱点识别
        LocalDateTime quiz1At = today.minusDays(6).atTime(20, 52);
        entities.add(LearningEvent.of(userId, "QUIZ").ref("QUIZ", null).score(80d).minutes(12)
                .detail(DemoScript.QUIZ1_TITLE).at(quiz1At));
        recordPoint(entities, userId, "前序遍历", 100d, quiz1At);
        recordPoint(entities, userId, "中序遍历", 100d, quiz1At);
        recordPoint(entities, userId, "后序遍历", 100d, quiz1At);
        recordPoint(entities, userId, "层序遍历", 100d, quiz1At);
        recordPoint(entities, userId, "非递归遍历", 40d, quiz1At);

        LocalDateTime quiz2At = today.minusDays(3).atTime(20, 58);
        entities.add(LearningEvent.of(userId, "QUIZ").ref("QUIZ", null).score(75d).minutes(10)
                .detail(DemoScript.QUIZ2_TITLE).at(quiz2At));
        recordPoint(entities, userId, "邻接表", 100d, quiz2At);
        recordPoint(entities, userId, "BFS最短路", 100d, quiz2At);
        recordPoint(entities, userId, "Dijkstra", 100d, quiz2At);
        recordPoint(entities, userId, "拓扑排序", 40d, quiz2At);

        events.saveAll(entities);
    }

    private void recordPoint(List<LearningEvent> entities, long userId, String point, double score,
                             LocalDateTime at) {
        entities.add(LearningEvent.of(userId, "QUIZ_ITEM")
                .point(point)
                .score(score)
                .minutes(0)
                .detail(point + (score >= 100 ? " · 答对" : " · 答错"))
                .at(at));
    }

    private void seedAiLogs(long userId, LocalDate today) {
        List<AiUsageLog> entities = new ArrayList<>();
        for (Object[] row : DemoScript.AI_LOGS) {
            int daysAgo = (int) row[0];
            entities.add(AiUsageLog.of(userId, (String) row[1], (String) row[2])
                    .tokens((int) row[3], (int) row[4])
                    .latency((int) row[5])
                    .status((String) row[6], (String) row[8])
                    .blocked((boolean) row[7])
                    .at(today.minusDays(daysAgo).atTime(20, 30)));
        }
        aiLogs.saveAll(entities);
    }
}
