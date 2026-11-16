package com.zhixue.engine.chat;

import com.zhixue.engine.chat.ChatDtos.ChatRequest;
import com.zhixue.engine.chat.ChatDtos.ConversationView;
import com.zhixue.engine.chat.ChatDtos.CreateConversationRequest;
import com.zhixue.engine.chat.ChatDtos.MessageView;
import com.zhixue.engine.common.ApiResult;
import com.zhixue.engine.security.UserContext;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/conversations")
    public ApiResult<List<ConversationView>> conversations() {
        return ApiResult.ok(chatService.list(UserContext.requireUserId()));
    }

    @PostMapping("/conversations")
    public ApiResult<ConversationView> create(@RequestBody(required = false) CreateConversationRequest request) {
        return ApiResult.ok(chatService.create(UserContext.requireUserId(), request));
    }

    @DeleteMapping("/conversations/{id}")
    public ApiResult<Void> delete(@PathVariable long id) {
        chatService.delete(UserContext.requireUserId(), id);
        return ApiResult.ok();
    }

    @GetMapping("/conversations/{id}/messages")
    public ApiResult<List<MessageView>> messages(@PathVariable long id) {
        return ApiResult.ok(chatService.history(UserContext.requireUserId(), id));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestBody ChatRequest request) {
        SseEmitter emitter = chatService.newEmitter();
        chatService.stream(UserContext.requireUserId(), request, emitter);
        return emitter;
    }
}
