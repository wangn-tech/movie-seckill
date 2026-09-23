package com.wangning.seckill.service;

/**
 * 令牌桶限流服务接口。
 */
public interface RateLimiterService {

    /**
     * 尝试获取一个令牌。
     * @return true=放行, false=被限流
     */
    boolean tryAcquire(String identity, String api, int rate, int capacity);
}
