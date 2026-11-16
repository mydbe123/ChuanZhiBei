package com.zhixue.engine.user;

import java.time.LocalDateTime;

public record UserView(
        Long id,
        String username,
        String displayName,
        String email,
        String role,
        String school,
        String grade,
        String major,
        String learningGoal,
        String level,
        String avatarColor,
        LocalDateTime createdAt
) {
}
