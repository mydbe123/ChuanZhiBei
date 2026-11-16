package com.zhixue.engine.plan;

import com.zhixue.engine.common.ApiResult;
import com.zhixue.engine.plan.PlanDtos.GenerateRequest;
import com.zhixue.engine.plan.PlanDtos.PlanDetail;
import com.zhixue.engine.plan.PlanDtos.PlanSummary;
import com.zhixue.engine.plan.PlanDtos.TaskStatusRequest;
import com.zhixue.engine.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plan")
public class PlanController {

    private final PlanService planService;

    public PlanController(PlanService planService) {
        this.planService = planService;
    }

    @PostMapping("/generate")
    public ApiResult<PlanDetail> generate(@RequestBody GenerateRequest request) {
        return ApiResult.ok(planService.generate(UserContext.requireUserId(), request.goal(),
                request.level(), request.days(), request.dailyMinutes()));
    }

    @GetMapping
    public ApiResult<List<PlanSummary>> list() {
        return ApiResult.ok(planService.list(UserContext.requireUserId()));
    }

    @GetMapping("/{id}")
    public ApiResult<PlanDetail> detail(@PathVariable long id) {
        return ApiResult.ok(planService.detail(UserContext.requireUserId(), id));
    }

    @PatchMapping("/tasks/{taskId}")
    public ApiResult<PlanDetail> updateTask(@PathVariable long taskId,
                                            @RequestBody TaskStatusRequest request) {
        return ApiResult.ok(planService.updateTaskStatus(UserContext.requireUserId(), taskId,
                request.status()));
    }
}
