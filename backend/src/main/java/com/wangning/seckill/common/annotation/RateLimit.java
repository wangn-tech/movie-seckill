package com.wangning.seckill.common.annotation;

import com.wangning.seckill.common.enums.RateLimitGranularity;

import java.lang.annotation.*;

/**
 * 限流注解：标在 Controller 方法上，AOP 切面拦截。
 *
 * <p>示例：@RateLimit(rate = 10, capacity = 20, granularity = RateLimitGranularity.USER)
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /** 每秒补充令牌数 */
    int rate() default 10;

    /** 桶容量（突发流量） */
    int capacity() default 20;

    /** 限流粒度：按用户 / IP / 全局 */
    RateLimitGranularity granularity() default RateLimitGranularity.USER;
}
