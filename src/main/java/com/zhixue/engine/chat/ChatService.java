package com.zhixue.engine.chat;

import com.zhixue.engine.agent.AgentEventSink;
import com.zhixue.engine.agent.AgentOrchestrator;
import com.zhixue.engine.agent.AgentResult;
import com.zhixue.engine.analytics.AiUsageService;
import com.zhixue.engine.analytics.LearningEventService;
import com.zhixue.engine.chat.ChatDtos.ChatRequest;
import com.zhixue.engine.chat.ChatDtos.ConversationView;
import com.zhixue.engine.chat.ChatDtos.CreateConversationRequest;
import com.zhixue.engine.chat.ChatDtos.MessageView;
import com.zhixue.engine.common.BusinessException;
import com.zhixue.engine.common.Json;
import com.zhixue.engine.config.AppProperties;
import com.zhixue.engine.llm.DeepSeekClient;
import com.zhixue.engine.rag.KnowledgeService;
import com.zhixue.engine.rag.RetrievalHit;
import com.zhixue.engine.safety.ContentGuard;
import com.zhixue.engine.safety.RateLimiter;
import com.zhixue.engine.user.AuthService;
import com.zhixue.engine.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int HISTORY_LIMIT = 10;
    private static final int MAX_HISTORY_CHARS = 2000;
    private static final long SSE_TIMEOUT_MS = 300_000L;

    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final AuthService authService;
    private final AgentOrchestrator orchestrator;
    private final ContentGuard guard;
    private final RateLimiter rateLimiter;
    private final KnowledgeService knowledgeService;
    private final AiUsageService aiUsage;
    private final LearningEventService learningEvents;
    private final AppProperties properties;
    private final Json json;
    private final Executor executor;

    public ChatService(ConversationRepository conversations, MessageRepository messages, AuthService authService,
                       AgentOrchestrator orchestrator, ContentGuard guard, RateLimiter rateLimiter,
                       KnowledgeService knowledgeService, AiUsageService aiUsage,
                       LearningEventService learningEvents, AppProperties properties, Json json,
                       @Qualifier("agentExecutor") Executor executor) {
        this.conversations = conversations;
        this.messages = messages;
        this.authService = authService;
        this.orchestrator = orchestrator;
        this.guard = guard;
        this.rateLimiter = rateLimiter;
        this.knowledgeService = knowledgeService;
        this.aiUsage = aiUsage;
        this.learningEvents = learningEvents;
        this.properties = properties;
        this.json = json;
        this.executor = executor;
    }

    public List<ConversationView> list(long userId) {
        return conversations.findByUserIdOrderByUpdatedAtDesc(userId).stream()
                .map(ConversationView::of).toList();
    }

    @Transactional
    public ConversationView create(long userId, CreateConversationRequest request) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(request != null && request.title() != null && !request.title().isBlank()
                ? request.title().trim() : "新的对话");
        conversation.setScene(request != null && request.scene() != null ? request.scene() : "TUTOR");
        return ConversationView.of(conversations.save(conversation));
    }

    @Transactional
    public void delete(long userId, long conversationId) {
        Conversation conversation = requireOwned(conversationId, userId);
        messages.deleteByConversationId(conversationId);
        conversations.delete(conversation);
    }

    public List<MessageView> history(long userId, long conversationId) {
        requireOwned(conversationId, userId);
        return messages.findByConversationIdOrderByIdAsc(conversationId).stream()
                .map(m -> new MessageView(m.getId(), m.getRole(), m.getContent(), m.getReasoning(),
                        json.toMapList(m.getToolTrace()), json.toMapList(m.getCitations()), m.getCreatedAt()))
                .toList();
    }

    public SseEmitter newEmitter() {
        return new SseEmitter(SSE_TIMEOUT_MS);
    }

    public void stream(long userId, ChatRequest request, SseEmitter emitter) {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        emitter.onCompletion(() -> cancelled.set(true));
        emitter.onError(e -> cancelled.set(true));
        emitter.onTimeout(() -> {
            cancelled.set(true);
            emitter.complete();
        });
        SseSink sink = new SseSink(emitter, cancelled);

        try {
            String raw = request.content() == null ? "" : request.content();
            boolean hasImages = request.images() != null && !request.images().isEmpty();
            if (raw.isBlank() && !hasImages) {
                sink.error("请输入你的问题～");
                emitter.complete();
                return;
            }

            ContentGuard.Verdict verdict = guard.inspect(raw);
            if (!verdict.allowed()) {
                sendBlocked(sink, emitter, userId, verdict.message());
                return;
            }
            if (!rateLimiter.tryAcquire(userId)) {
                sink.error("提问太频繁啦，请稍等几十秒再试（每分钟上限 " + rateLimiter.limitPerMinute() + " 次）");
                emitter.complete();
                return;
            }

            User user = authService.require(userId);
            Conversation conversation = resolveConversation(userId, request);
            List<Map<String, Object>> history = loadHistory(conversation.getId());

            Message userMessage = messages.save(Message.of(conversation.getId(), "user", verdict.cleanedText()));
            if (conversation.getMessageCount() == 0) {
                conversation.setTitle(titleFrom(verdict.cleanedText(), hasImages));
            }
            conversation.setMessageCount(conversation.getMessageCount() + 1);
            conversation.touch();
            conversations.save(conversation);

            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("conversationId", conversation.getId());
            meta.put("userMessageId", userMessage.getId());
            meta.put("title", conversation.getTitle());
            sink.send("meta", meta);

            boolean useKnowledge = Boolean.TRUE.equals(request.useKnowledge());
            List<RetrievalHit> knowledge = List.of();
            if (useKnowledge) {
                knowledge = knowledgeService.search(userId, verdict.cleanedText(), properties.rag().topK());
            } else {
                // 未开启 RAG 时仍做一次轻量检索，用于判断是否需要提示"可开启知识库"
                knowledge = List.of();
            }

            final List<RetrievalHit> grounded = knowledge;
            final List<String> images = hasImages ? request.images() : List.of();
            final long conversationId = conversation.getId();
            final String question = verdict.cleanedText();
            final boolean injection = verdict.injected();

            executor.execute(() -> execute(user, conversationId, history, question, images, grounded,
                    injection, sink, emitter, cancelled));
        } catch (BusinessException e) {
            sink.error(e.getMessage());
            emitter.complete();
        } catch (Exception e) {
            log.error("对话启动失败", e);
            sink.error("服务暂时不可用，请稍后再试");
            emitter.complete();
        }
    }

    private void sendBlocked(SseSink sink, SseEmitter emitter, long userId, String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("delta", message);
        sink.send("delta", payload);
        Map<String, Object> done = new LinkedHashMap<>();
        done.put("blocked", true);
        sink.send("done", done);
        emitter.complete();
        aiUsage.record(userId, "CHAT", "guard", 0, 0, 0, "BLOCKED", true, "内容安全策略拦截");
    }

    private void execute(User user, long conversationId, List<Map<String, Object>> history, String question,
                         List<String> images, List<RetrievalHit> knowledge, boolean injection,
                         SseSink sink, SseEmitter emitter, AtomicBoolean cancelled) {
        long start = System.currentTimeMillis();
        try {
            AgentResult result = orchestrator.run(user, history, question, images, knowledge, injection,
                    conversationId, sink);
            String content = guard.postProcess(result.content());
            if (content == null || content.isBlank()) {
                content = "这轮我没能组织出有效回答，换个问法或补充一点背景，我再试一次？";
                sink.send("delta", Map.of("delta", content));
            }
            Message assistant = Message.of(conversationId, "assistant", content);
            assistant.setReasoning(blankToNull(result.reasoning()));
            assistant.setToolTrace(result.toolTrace().isEmpty() ? null : json.write(result.toolTrace()));
            assistant.setCitations(result.citations().isEmpty() ? null : json.write(result.citations()));
            assistant.setTokens(result.promptTokens() + result.completionTokens());
            messages.save(assistant);

            Conversation conversation = conversations.findById(conversationId).orElse(null);
            if (conversation != null) {
                conversation.setMessageCount(conversation.getMessageCount() + 1);
                conversation.touch();
                conversations.save(conversation);
            }

            long latency = System.currentTimeMillis() - start;
            aiUsage.record(user.getId(), "CHAT", result.model(), result.promptTokens(),
                    result.completionTokens(), latency, "OK", false, null);
            learningEvents.record(user.getId(), "CHAT", "CONVERSATION", conversationId, null, null, 1,
                    question);

            Map<String, Object> usage = new LinkedHashMap<>();
            usage.put("promptTokens", result.promptTokens());
            usage.put("completionTokens", result.completionTokens());
            usage.put("latencyMs", latency);
            sink.send("usage", usage);

            Map<String, Object> done = new LinkedHashMap<>();
            done.put("conversationId", conversationId);
            done.put("messageId", assistant.getId());
            done.put("cancelled", result.cancelled() || cancelled.get());
            sink.send("done", done);
            emitter.complete();
        } catch (Exception e) {
            log.error("Agent 执行失败 conversationId={}", conversationId, e);
            aiUsage.record(user.getId(), "CHAT", "unknown", 0, 0, System.currentTimeMillis() - start,
                    "ERROR", false, e.getMessage());
            sink.error("AI 服务暂时不可用：" + rootMessage(e));
            try {
                emitter.complete();
            } catch (Exception ignored) {
                // 连接可能已断开
            }
        }
    }

    private Conversation resolveConversation(long userId, ChatRequest request) {
        if (request.conversationId() != null) {
            return requireOwned(request.conversationId(), userId);
        }
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle("新的对话");
        conversation.setScene("TUTOR");
        return conversations.save(conversation);
    }

    private Conversation requireOwned(long conversationId, long userId) {
        return conversations.findByIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new BusinessException(404, "会话不存在"));
    }

    private List<Map<String, Object>> loadHistory(long conversationId) {
        List<Message> all = messages.findByConversationIdOrderByIdAsc(conversationId);
        int from = Math.max(0, all.size() - HISTORY_LIMIT);
        List<Map<String, Object>> history = new ArrayList<>();
        for (int i = from; i < all.size(); i++) {
            Message message = all.get(i);
            if (!"user".equals(message.getRole()) && !"assistant".equals(message.getRole())) {
                continue;
            }
            String text = message.getContent();
            if (text == null || text.isBlank()) {
                continue;
            }
            history.add(DeepSeekClient.message(message.getRole(),
                    text.length() > MAX_HISTORY_CHARS ? text.substring(0, MAX_HISTORY_CHARS) : text));
        }
        return history;
    }

    private String titleFrom(String text, boolean hasImages) {
        if (text == null || text.isBlank()) {
            return hasImages ? "图片提问" : "新的对话";
        }
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() > 22 ? cleaned.substring(0, 22) + "…" : cleaned;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String rootMessage(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
        }
        String message = cursor.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    /** 把 Agent 事件映射为 SSE 事件。 */
    private static final class SseSink implements AgentEventSink {

        private final SseEmitter emitter;
        private final AtomicBoolean cancelled;

        private SseSink(SseEmitter emitter, AtomicBoolean cancelled) {
            this.emitter = emitter;
            this.cancelled = cancelled;
        }

        @Override
        public void onStep(int step, int maxSteps) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("step", step);
            payload.put("maxSteps", maxSteps);
            send("step", payload);
        }

        @Override
        public void onReasoning(String delta) {
            send("reasoning", Map.of("delta", delta));
        }

        @Override
        public void onContent(String delta) {
            send("delta", Map.of("delta", delta));
        }

        @Override
        public void onToolCall(String id, String name, String arguments) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("name", name);
            payload.put("arguments", arguments);
            send("tool_call", payload);
        }

        @Override
        public void onToolResult(String id, String name, String summary, Map<String, Object> artifact) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", id);
            payload.put("name", name);
            payload.put("summary", summary);
            payload.put("artifact", artifact);
            send("tool_result", payload);
        }

        @Override
        public void onCitations(List<Map<String, Object>> citations) {
            send("citations", Map.of("items", citations));
        }

        @Override
        public boolean cancelled() {
            return cancelled.get();
        }

        private void error(String message) {
            send("error", Map.of("message", message));
        }

        private void send(String event, Object data) {
            if (cancelled.get()) {
                return;
            }
            try {
                emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
            } catch (Exception e) {
                cancelled.set(true);
            }
        }
    }
}
