package com.zhixue.engine.quiz;

import com.zhixue.engine.common.ApiResult;
import com.zhixue.engine.quiz.QuizDtos.GenerateRequest;
import com.zhixue.engine.quiz.QuizDtos.QuizDetail;
import com.zhixue.engine.quiz.QuizDtos.QuizSummary;
import com.zhixue.engine.quiz.QuizDtos.SubmitRequest;
import com.zhixue.engine.quiz.QuizDtos.SubmitResult;
import com.zhixue.engine.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/quiz")
public class QuizController {

    private final QuizService quizService;

    public QuizController(QuizService quizService) {
        this.quizService = quizService;
    }

    @PostMapping("/generate")
    public ApiResult<QuizDetail> generate(@RequestBody GenerateRequest request) {
        return ApiResult.ok(quizService.generate(UserContext.requireUserId(), request.topic(),
                request.difficulty(), request.count()));
    }

    @GetMapping
    public ApiResult<List<QuizSummary>> list() {
        return ApiResult.ok(quizService.list(UserContext.requireUserId()));
    }

    @GetMapping("/{id}")
    public ApiResult<QuizDetail> detail(@PathVariable long id) {
        return ApiResult.ok(quizService.detail(UserContext.requireUserId(), id));
    }

    @PostMapping("/{id}/submit")
    public ApiResult<SubmitResult> submit(@PathVariable long id, @RequestBody SubmitRequest request) {
        return ApiResult.ok(quizService.submit(UserContext.requireUserId(), id, request.answers()));
    }

    @GetMapping("/{id}/result")
    public ApiResult<SubmitResult> result(@PathVariable long id) {
        return ApiResult.ok(quizService.review(UserContext.requireUserId(), id));
    }
}
