package com.wangning.seckill.service;

import com.wangning.seckill.common.constant.CacheKeyConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 令牌桶限流服务：执行 Redis Lua 脚本。
 */
@Service
public class RateLimiterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RateLimiterService(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        this.script.setResultType(Long.class);
    }

    /**
     * 尝试获取一个令牌。
     *
     * @param identity 限流维度标识（userId / IP / "global"）
     * @param api      接口标识
     * @param rate     每秒补充速率
     * @param capacity 桶容量
     * @return true=放行, false=被限流
     */
    public boolean tryAcquire(String identity, String api, int rate, int capacity) {
        String key = CacheKeyConstants.rateLimitKey(identity, api);
        Long result = redis.execute(script, List.of(key),
                String.valueOf(rate),
                String.valueOf(capacity),
                String.valueOf(System.currentTimeMillis()));
        return result != null && result == 1L;
    }
}
