package com.zhixue.engine.user;

import com.zhixue.engine.common.ApiResult;
import com.zhixue.engine.security.UserContext;
import com.zhixue.engine.user.AuthDtos.AuthResponse;
import com.zhixue.engine.user.AuthDtos.LoginRequest;
import com.zhixue.engine.user.AuthDtos.RegisterRequest;
import com.zhixue.engine.user.AuthDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ApiResult<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResult.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ApiResult<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResult.ok(authService.login(request));
    }

    @PostMapping("/demo")
    public ApiResult<AuthResponse> demo() {
        return ApiResult.ok(authService.demoLogin());
    }

    @GetMapping("/me")
    public ApiResult<UserView> me() {
        return ApiResult.ok(authService.profile(UserContext.requireUserId()));
    }

    @PutMapping("/me")
    public ApiResult<UserView> updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return ApiResult.ok(authService.updateProfile(UserContext.requireUserId(), request));
    }
}
