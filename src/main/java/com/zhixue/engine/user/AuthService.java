package com.zhixue.engine.user;

import com.zhixue.engine.common.BusinessException;
import com.zhixue.engine.security.JwtService;
import com.zhixue.engine.security.PasswordService;
import com.zhixue.engine.user.AuthDtos.AuthResponse;
import com.zhixue.engine.user.AuthDtos.LoginRequest;
import com.zhixue.engine.user.AuthDtos.RegisterRequest;
import com.zhixue.engine.user.AuthDtos.UpdateProfileRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    public static final String DEMO_USERNAME = "demo";
    private static final String DEMO_PASSWORD = "demo123456";

    private final UserRepository users;
    private final PasswordService passwordService;
    private final JwtService jwtService;

    public AuthService(UserRepository users, PasswordService passwordService, JwtService jwtService) {
        this.users = users;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        if (users.existsByUsername(username)) {
            throw new BusinessException(409, "该用户名已被占用");
        }
        User user = new User();
        user.setUsername(username);
        user.setPasswordHash(passwordService.hash(request.password()));
        user.setDisplayName(request.displayName().trim());
        user.setSchool(blankToNull(request.school()));
        user.setGrade(blankToNull(request.grade()));
        user.setMajor(blankToNull(request.major()));
        user.setLearningGoal(blankToNull(request.learningGoal()));
        user.setLevel(normalizeLevel(request.level()));
        user.setAvatarColor(pickAvatarColor(username));
        users.save(user);
        return new AuthResponse(jwtService.issue(user.getId(), user.getUsername()), user.toView());
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = users.findByUsername(request.username().trim())
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordService.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return new AuthResponse(jwtService.issue(user.getId(), user.getUsername()), user.toView());
    }

    /**
     * 评审体验账号：无需注册即可进入完整功能，便于在线演示。
     */
    @Transactional
    public AuthResponse demoLogin() {
        User user = users.findByUsername(DEMO_USERNAME).orElseGet(() -> {
            User demo = new User();
            demo.setUsername(DEMO_USERNAME);
            demo.setPasswordHash(passwordService.hash(DEMO_PASSWORD));
            demo.setDisplayName("体验同学");
            demo.setSchool("示范大学");
            demo.setGrade("大二");
            demo.setMajor("计算机科学与技术");
            demo.setLearningGoal("两个月内系统掌握数据结构与算法，冲击蓝桥杯省一");
            demo.setLevel("BEGINNER");
            demo.setAvatarColor("#6366F1");
            return users.save(demo);
        });
        return new AuthResponse(jwtService.issue(user.getId(), user.getUsername()), user.toView());
    }

    @Transactional(readOnly = true)
    public UserView profile(long userId) {
        return mustFind(userId).toView();
    }

    @Transactional(readOnly = true)
    public User require(long userId) {
        return mustFind(userId);
    }

    @Transactional
    public UserView updateProfile(long userId, UpdateProfileRequest request) {
        User user = mustFind(userId);
        if (hasText(request.displayName())) {
            user.setDisplayName(request.displayName().trim());
        }
        if (request.email() != null) {
            user.setEmail(blankToNull(request.email()));
        }
        if (request.school() != null) {
            user.setSchool(blankToNull(request.school()));
        }
        if (request.grade() != null) {
            user.setGrade(blankToNull(request.grade()));
        }
        if (request.major() != null) {
            user.setMajor(blankToNull(request.major()));
        }
        if (request.learningGoal() != null) {
            user.setLearningGoal(blankToNull(request.learningGoal()));
        }
        if (hasText(request.level())) {
            user.setLevel(normalizeLevel(request.level()));
        }
        if (hasText(request.avatarColor())) {
            user.setAvatarColor(request.avatarColor());
        }
        users.save(user);
        return user.toView();
    }

    private User mustFind(long userId) {
        return users.findById(userId).orElseThrow(() -> new BusinessException(404, "用户不存在"));
    }

    private String normalizeLevel(String level) {
        if (level == null) {
            return "BEGINNER";
        }
        String normalized = level.trim().toUpperCase();
        return switch (normalized) {
            case "BEGINNER", "INTERMEDIATE", "ADVANCED" -> normalized;
            default -> "BEGINNER";
        };
    }

    private String pickAvatarColor(String seed) {
        String[] palette = {"#4F46E5", "#0EA5E9", "#10B981", "#F59E0B", "#EC4899", "#8B5CF6"};
        return palette[Math.floorMod(seed.hashCode(), palette.length)];
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }
}
