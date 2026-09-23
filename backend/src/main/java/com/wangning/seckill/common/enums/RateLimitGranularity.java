package com.wangning.seckill.common.enums;

/**
 * 限流粒度枚举 — 策略模式选择。
 */
public enum RateLimitGranularity {
    /** 按用户 ID 限流（从 ThreadLocal 取） */
    USER,
    /** 按 IP 限流 */
    IP,
    /** 全局接口维度限流 */
    GLOBAL
}
