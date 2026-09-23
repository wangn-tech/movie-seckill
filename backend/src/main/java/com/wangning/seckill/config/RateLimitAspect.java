package com.wangning.seckill.config;

import com.wangning.seckill.common.annotation.RateLimit;
import com.wangning.seckill.common.context.UserContextHolder;
import com.wangning.seckill.common.enums.RateLimitGranularity;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 限流切面：拦截 @RateLimit 注解，按粒度执行令牌桶。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class RateLimitAspect {

    private final RateLimiterService rateLimiter;

    @Around("@annotation(rateLimit)")
    public Object around(ProceedingJoinPoint pjp, RateLimit rateLimit) throws Throwable {
        String identity = resolveIdentity(rateLimit.granularity());
        String api = pjp.getSignature().toShortString();

        boolean passed = rateLimiter.tryAcquire(identity, api,
                rateLimit.rate(), rateLimit.capacity());

        if (!passed) {
            log.warn("限流触发: identity={}, api={}", identity, api);
            throw new BizException(ResultCode.TOO_MANY_REQUESTS);
        }
        return pjp.proceed();
    }

    private String resolveIdentity(RateLimitGranularity g) {
        return switch (g) {
            case USER -> {
                Long uid = UserContextHolder.getUserId();
                yield uid == null ? "anon" : "u" + uid;
            }
            case IP -> {
                ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
                String ip = "unknown";
                if (attrs != null) {
                    HttpServletRequest req = attrs.getRequest();
                    ip = req.getRemoteAddr();
                }
                yield "ip:" + ip;
            }
            case GLOBAL -> "global";
        };
    }
}
