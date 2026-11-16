package com.zhixue.engine.security;

import com.zhixue.engine.common.BusinessException;

/**
 * 当前请求的登录用户。由 {@link AuthInterceptor} 写入并在请求结束后清理。
 */
public final class UserContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        USER_ID.set(userId);
    }

    public static Long peek() {
        return USER_ID.get();
    }

    public static long requireUserId() {
        Long id = USER_ID.get();
        if (id == null) {
            throw new BusinessException(401, "请先登录");
        }
        return id;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
