package com.wangning.seckill.config;

import com.wangning.seckill.common.context.UserContextHolder;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.common.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 认证拦截器：从 Authorization 头解析 AccessToken，把 userId 放进 ThreadLocal。
 *
 * <p>afterCompletion 必须 clear ThreadLocal，防止线程池复用导致用户串号。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtUtil jwtUtil;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse resp, Object handler) {
        String header = req.getHeader("Authorization");
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String token = header.substring(7);
        try {
            Long userId = jwtUtil.parseUserId(token);
            UserContextHolder.setUserId(userId);
            return true;
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest req, HttpServletResponse resp, Object handler, Exception ex) {
        // 关键：防止 Tomcat 线程池复用导致用户上下文串号
        UserContextHolder.clear();
    }
}
