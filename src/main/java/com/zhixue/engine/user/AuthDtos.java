package com.zhixue.engine.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank(message = "不能为空") @Size(min = 3, max = 32, message = "长度需为 3-32 个字符") String username,
            @NotBlank(message = "不能为空") @Size(min = 6, max = 64, message = "至少 6 位") String password,
            @NotBlank(message = "不能为空") @Size(max = 32) String displayName,
            String school,
            String grade,
            String major,
            String learningGoal,
            String level
    ) {
    }

    public record LoginRequest(
            @NotBlank(message = "不能为空") String username,
            @NotBlank(message = "不能为空") String password
    ) {
    }

    public record UpdateProfileRequest(
            @Size(max = 32) String displayName,
            @Size(max = 120) String email,
            @Size(max = 128) String school,
            @Size(max = 32) String grade,
            @Size(max = 64) String major,
            @Size(max = 255) String learningGoal,
            String level,
            String avatarColor
    ) {
    }

    public record AuthResponse(String token, UserView user) {
    }
}
