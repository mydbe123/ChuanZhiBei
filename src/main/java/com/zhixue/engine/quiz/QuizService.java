package com.zhixue.engine.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.zhixue.engine.analytics.AiUsageService;
import com.zhixue.engine.analytics.LearningEventService;
import com.zhixue.engine.common.BusinessException;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.DeepSeekProperties;
import com.zhixue.engine.llm.DeepSeekClient;
import com.zhixue.engine.llm.LlmResult;
import com.zhixue.engine.llm.StructuredGenerator;
import com.zhixue.engine.quiz.QuizDtos.AnswerInput;
import com.zhixue.engine.quiz.QuizDtos.Option;
import com.zhixue.engine.quiz.QuizDtos.QuestionResult;
import com.zhixue.engine.quiz.QuizDtos.QuestionView;
import com.zhixue.engine.quiz.QuizDtos.QuizDetail;
import com.zhixue.engine.quiz.QuizDtos.QuizSummary;
import com.zhixue.engine.quiz.QuizDtos.SubmitResult;
import com.zhixue.engine.rag.KnowledgeService;
import com.zhixue.engine.rag.RetrievalHit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuizService {

    private static final Logger log = LoggerFactory.getLogger(QuizService.class);

    private static final String SYSTEM_PROMPT = """
            你是「智学引擎」的命题专家，服务于中国高校计算机与通识课程。
            你的任务是根据给定主题与参考资料，生成高质量单选题。
            硬性要求：
            1) 只输出一个 JSON 对象，不要输出 markdown 代码块、解释或前后缀文字；
            2) 每题 4 个选项，key 依次为 A/B/C/D，有且仅有一个正确答案；
            3) 题干必须自洽、无歧义，避免"以上都对/都不对"这类选项；
            4) 每题给出 explanation（解析，80 字以内）与 knowledgePoint（知识点，8 字以内）；
            5) 题目之间不得重复，且尽量覆盖不同知识点；
            6) 输出结构：
            {"title":"测验标题","questions":[{"stem":"题干","options":[{"key":"A","text":"选项"}],"answer":"A","explanation":"解析","knowledgePoint":"知识点"}]}
            """;

    private final QuizRepository quizzes;
    private final QuizQuestionRepository questions;
    private final StructuredGenerator generator;
    private final DeepSeekClient client;
    private final DeepSeekProperties props;
    private final KnowledgeService knowledgeService;
    private final LearningEventService learningEvents;
    private final AiUsageService aiUsage;
    private final Json json;

    public QuizService(QuizRepository quizzes, QuizQuestionRepository questions, StructuredGenerator generator,
                       DeepSeekClient client, DeepSeekProperties props, KnowledgeService knowledgeService,
                       LearningEventService learningEvents, AiUsageService aiUsage, Json json) {
        this.quizzes = quizzes;
        this.questions = questions;
        this.generator = generator;
        this.client = client;
        this.props = props;
        this.knowledgeService = knowledgeService;
        this.learningEvents = learningEvents;
        this.aiUsage = aiUsage;
        this.json = json;
    }

    public QuizDetail generate(long userId, String topic, String difficulty, Integer count) {
        String safeTopic = topic == null || topic.isBlank() ? "数据结构与算法基础" : topic.trim();
        String safeDifficulty = normalizeDifficulty(difficulty);
        int size = count == null ? 5 : Math.max(3, Math.min(10, count));

        List<RetrievalHit> hits = knowledgeService.search(userId, safeTopic, 3);
        String context = hits.isEmpty() ? "（无匹配的本地资料，请依据学科通用知识命题）"
                : hits.stream()
                .map(h -> "《" + h.documentTitle() + "》片段：" + h.snippet())
                .collect(Collectors.joining("\n"));

        String userPrompt = """
                主题：%s
                难度：%s（EASY=基础记忆，MEDIUM=理解应用，HARD=综合辨析）
                题目数量：%d
                参考资料（可参考但不要照抄，若与主题无关可忽略）：
                %s
                请生成 JSON。
                """.formatted(safeTopic, safeDifficulty, size, context);

        StructuredGenerator.Result result = generator.generate("quiz-generate", SYSTEM_PROMPT, userPrompt);
        aiUsage.record(userId, "QUIZ_GENERATE", result.model(), result.promptTokens(),
                result.completionTokens(), result.latencyMs(), "OK", false, safeTopic);

        JsonNode root = result.data();
        JsonNode items = root.path("questions");
        if (!items.isArray() || items.isEmpty()) {
            throw new BusinessException(500, "AI 未能生成有效题目，请换个主题再试");
        }

        Quiz quiz = new Quiz();
        quiz.setUserId(userId);
        quiz.setTopic(safeTopic.length() > 200 ? safeTopic.substring(0, 200) : safeTopic);
        String title = root.path("title").asText("");
        quiz.setTitle(title.isBlank() ? safeTopic + " · AI 测验" : title);
        quiz.setDifficulty(safeDifficulty);
        quiz.setQuestionCount(items.size());
        quizzes.save(quiz);

        List<QuizQuestion> entities = new ArrayList<>();
        int seq = 1;
        for (JsonNode item : items) {
            String stem = item.path("stem").asText("").trim();
            List<Option> options = parseOptions(item.path("options"));
            String answer = item.path("answer").asText("").trim().toUpperCase(Locale.ROOT);
            if (stem.isBlank() || options.size() < 2 || answer.isBlank()) {
                continue;
            }
            QuizQuestion question = new QuizQuestion();
            question.setQuizId(quiz.getId());
            question.setSeq(seq++);
            question.setStem(stem);
            question.setOptions(json.write(options));
            question.setAnswer(answer);
            question.setExplanation(item.path("explanation").asText(""));
            String point = item.path("knowledgePoint").asText("");
            question.setKnowledgePoint(point.isBlank() ? null : truncate(point, 60));
            entities.add(question);
        }
        if (entities.isEmpty()) {
            throw new BusinessException(500, "AI 返回的题目格式不正确，请重试");
        }
        questions.saveAll(entities);
        quiz.setQuestionCount(entities.size());
        quizzes.save(quiz);
        return detail(userId, quiz.getId());
    }

    public List<QuizSummary> list(long userId) {
        return quizzes.findByUserIdOrderByIdDesc(userId).stream().map(QuizSummary::of).toList();
    }

    public QuizDetail detail(long userId, long quizId) {
        Quiz quiz = requireOwned(quizId, userId);
        List<QuestionView> views = questions.findByQuizIdOrderBySeqAsc(quizId).stream()
                .map(q -> new QuestionView(q.getId(), q.getType(), q.getStem(),
                        parseOptions(json.read(q.getOptions() == null ? "[]" : q.getOptions())),
                        q.getKnowledgePoint()))
                .toList();
        return new QuizDetail(quiz.getId(), quiz.getTitle(), quiz.getTopic(), quiz.getDifficulty(),
                quiz.getStatus(), quiz.getScore(), quiz.getQuestionCount(), quiz.getCreatedAt(), views);
    }

    /** 回看已提交测验的逐题解析（不重复调用大模型）。 */
    public SubmitResult review(long userId, long quizId) {
        Quiz quiz = requireOwned(quizId, userId);
        if (!"DONE".equals(quiz.getStatus())) {
            throw new BusinessException(409, "该测验尚未提交");
        }
        List<QuizQuestion> list = questions.findByQuizIdOrderBySeqAsc(quizId);
        List<QuestionResult> results = new ArrayList<>();
        Set<String> weak = new LinkedHashSet<>();
        int correctCount = 0;
        for (QuizQuestion question : list) {
            boolean ok = Boolean.TRUE.equals(question.getCorrect());
            if (ok) {
                correctCount++;
            } else if (question.getKnowledgePoint() != null) {
                weak.add(question.getKnowledgePoint());
            }
            results.add(new QuestionResult(question.getId(), question.getStem(),
                    parseOptions(json.read(question.getOptions() == null ? "[]" : question.getOptions())),
                    question.getAnswer(), question.getUserAnswer(), ok, question.getExplanation(),
                    question.getKnowledgePoint()));
        }
        String diagnosis = "该测验已完成，得分 %d 分。下方可逐题查看解析，错题知识点已计入你的掌握度统计。"
                .formatted(quiz.getScore() == null ? 0 : quiz.getScore());
        return new SubmitResult(quiz.getId(), quiz.getTitle(), quiz.getScore() == null ? 0 : quiz.getScore(),
                list.size(), correctCount, results, new ArrayList<>(weak), diagnosis);
    }

    public SubmitResult submit(long userId, long quizId, List<AnswerInput> answers) {        Quiz quiz = requireOwned(quizId, userId);
        if ("DONE".equals(quiz.getStatus())) {
            throw new BusinessException(409, "该测验已经提交过了");
        }
        List<QuizQuestion> list = questions.findByQuizIdOrderBySeqAsc(quizId);
        if (list.isEmpty()) {
            throw new BusinessException(404, "测验题目不存在");
        }
        Map<Long, String> submitted = new HashMap<>();
        if (answers != null) {
            answers.forEach(a -> submitted.put(a.questionId(), a.answer()));
        }

        int correctCount = 0;
        Set<String> weak = new LinkedHashSet<>();
        List<QuestionResult> results = new ArrayList<>();
        for (QuizQuestion question : list) {
            String userAnswer = submitted.get(question.getId());
            boolean ok = normalize(question.getAnswer()).equals(normalize(userAnswer));
            question.setUserAnswer(userAnswer);
            question.setCorrect(ok);
            if (ok) {
                correctCount++;
            } else if (question.getKnowledgePoint() != null) {
                weak.add(question.getKnowledgePoint());
            }
            results.add(new QuestionResult(question.getId(), question.getStem(),
                    parseOptions(json.read(question.getOptions() == null ? "[]" : question.getOptions())),
                    question.getAnswer(), userAnswer, ok, question.getExplanation(),
                    question.getKnowledgePoint()));
        }
        questions.saveAll(list);

        int score = Math.round(correctCount * 100f / list.size());
        quiz.setScore(score);
        quiz.setStatus("DONE");
        quiz.setCompletedAt(LocalDateTime.now());
        quizzes.save(quiz);

        int minutes = Math.max(3, list.size() * 2);
        learningEvents.record(userId, "QUIZ", "QUIZ", quiz.getId(), null, (double) score, minutes,
                quiz.getTitle());
        for (QuizQuestion question : list) {
            if (question.getKnowledgePoint() != null) {
                boolean ok = Boolean.TRUE.equals(question.getCorrect());
                learningEvents.record(userId, "QUIZ_ITEM", "QUIZ_QUESTION", question.getId(),
                        question.getKnowledgePoint(), ok ? 100d : 40d, 0,
                        question.getKnowledgePoint() + (ok ? " · 答对" : " · 答错"));
            }
        }

        String diagnosis = diagnose(quiz, results, correctCount, list.size());
        return new SubmitResult(quiz.getId(), quiz.getTitle(), score, list.size(), correctCount,
                results, new ArrayList<>(weak), diagnosis);
    }

    /** AI 学情诊断：失败时降级为规则化结论，保证主流程不受影响。 */
    private String diagnose(Quiz quiz, List<QuestionResult> results, int correct, int total) {
        String wrongPoints = results.stream()
                .filter(r -> !r.correct() && r.knowledgePoint() != null)
                .map(QuestionResult::knowledgePoint)
                .distinct()
                .collect(Collectors.joining("、"));
        String prompt = """
                学生完成了测验《%s》（主题：%s），共 %d 题，答对 %d 题。
                错题涉及知识点：%s
                请用 120 字以内给出学情诊断：先肯定进步，再指出薄弱点，最后给出一条具体可执行的改进建议。
                直接输出诊断文字，不要列表编号。
                """.formatted(quiz.getTitle(), quiz.getTopic(), total, correct,
                wrongPoints.isBlank() ? "无（全部答对）" : wrongPoints);
        long start = System.currentTimeMillis();
        try {
            LlmResult result = client.complete(props.model(),
                    List.of(DeepSeekClient.message("system", "你是一位温和而专业的学习规划师。"),
                            DeepSeekClient.message("user", prompt)), false);
            aiUsage.record(quiz.getUserId(), "QUIZ_DIAGNOSIS", props.model(), result.promptTokens(),
                    result.completionTokens(), System.currentTimeMillis() - start, "OK", false, null);
            String text = result.content() == null ? "" : result.content().trim();
            if (!text.isBlank()) {
                return text;
            }
        } catch (Exception e) {
            log.warn("学情诊断生成失败，使用规则化兜底: {}", e.getMessage());
        }
        if (wrongPoints.isBlank()) {
            return "本次测验全部答对，说明该主题基础扎实。建议把难度提升一档，或尝试用自己的话复述核心概念来检验是否真正掌握。";
        }
        return "本次答对 %d/%d 题，整体处于稳步提升阶段。薄弱点在「%s」，建议先回看相关知识卡片，再针对性练习 5 道同类题。"
                .formatted(correct, total, wrongPoints);
    }

    private Quiz requireOwned(long quizId, long userId) {
        Quiz quiz = quizzes.findById(quizId).orElseThrow(() -> new BusinessException(404, "测验不存在"));
        if (quiz.getUserId() != userId) {
            throw new BusinessException(403, "无权访问他人的测验");
        }
        return quiz;
    }

    private List<Option> parseOptions(JsonNode node) {
        List<Option> options = new ArrayList<>();
        if (node == null || !node.isArray()) {
            return options;
        }
        int index = 0;
        for (JsonNode item : node) {
            if (item.isTextual()) {
                options.add(new Option(String.valueOf((char) ('A' + index++)), item.asText()));
                continue;
            }
            String key = item.path("key").asText("");
            String text = item.path("text").asText("");
            if (text.isBlank()) {
                text = item.asText("");
            }
            if (key.isBlank()) {
                key = String.valueOf((char) ('A' + index));
            }
            if (!text.isBlank()) {
                options.add(new Option(key.toUpperCase(Locale.ROOT), text));
            }
            index++;
        }
        return options;
    }

    private String normalizeDifficulty(String difficulty) {
        if (difficulty == null) {
            return "MEDIUM";
        }
        String value = difficulty.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "EASY", "MEDIUM", "HARD" -> value;
            default -> "MEDIUM";
        };
    }

    private String normalize(String answer) {
        return answer == null ? "" : answer.trim().toUpperCase(Locale.ROOT);
    }

    private String truncate(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }
}
