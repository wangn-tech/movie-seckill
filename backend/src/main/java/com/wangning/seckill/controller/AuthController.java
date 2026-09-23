package com.wangning.seckill.controller;

import com.wangning.seckill.common.context.UserContextHolder;
import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.dto.LoginReq;
import com.wangning.seckill.dto.RegisterReq;
import com.wangning.seckill.service.AuthService;
import com.wangning.seckill.vo.LoginVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "认证", description = "注册/登录/刷新Token/登出")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Long> register(@Valid @RequestBody RegisterReq req) {
        return Result.success(authService.register(req));
    }

    @Operation(summary = "登录，返回双 Token")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginReq req) {
        return Result.success(authService.login(req));
    }

    @Operation(summary = "用 RefreshToken 换新的 AccessToken")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@RequestHeader("Authorization") String header) {
        // RefreshToken 通过 Authorization: Bearer <token> 传
        String token = header.startsWith("Bearer ") ? header.substring(7) : header;
        return Result.success(authService.refresh(token));
    }

    @Operation(summary = "登出")
    @PostMapping("/logout")
    public Result<Void> logout() {
        authService.logout(UserContextHolder.requireUserId());
        return Result.success();
    }
}
