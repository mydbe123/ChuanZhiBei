package com.zhixue.engine.safety;

import com.zhixue.engine.config.AppProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 每用户滑动窗口限流，保护大模型配额并防止接口被刷。
 */
@Component
public class RateLimiter {

    private static final long WINDOW_MS = 60_000L;

    private final Map<Long, Deque<Long>> buckets = new ConcurrentHashMap<>();
    private final int limitPerMinute;

    public RateLimiter(AppProperties properties) {
        this.limitPerMinute = Math.max(1, properties.rateLimit().aiRequestsPerMinute());
    }

    public boolean tryAcquire(long userId) {
        long now = System.currentTimeMillis();
        Deque<Long> bucket = buckets.computeIfAbsent(userId, key -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > WINDOW_MS) {
                bucket.pollFirst();
            }
            if (bucket.size() >= limitPerMinute) {
                return false;
            }
            bucket.addLast(now);
            return true;
        }
    }

    public int limitPerMinute() {
        return limitPerMinute;
    }
}
