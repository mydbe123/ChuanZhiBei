package com.zhixue.engine.analytics;

import com.zhixue.engine.analytics.AnalyticsDtos.AiUsageItem;
import com.zhixue.engine.analytics.AnalyticsDtos.Overview;
import com.zhixue.engine.common.ApiResult;
import com.zhixue.engine.security.UserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public ApiResult<Overview> overview() {
        return ApiResult.ok(analyticsService.overview(UserContext.requireUserId()));
    }

    @GetMapping("/ai-usage")
    public ApiResult<List<AiUsageItem>> aiUsage() {
        return ApiResult.ok(analyticsService.aiUsage(UserContext.requireUserId()));
    }
}
