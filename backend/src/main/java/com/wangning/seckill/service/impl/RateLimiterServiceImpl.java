package com.wangning.seckill.service.impl;

import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.service.RateLimiterService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 令牌桶限流服务实现：执行 Redis Lua 脚本。
 */
@Service
public class RateLimiterServiceImpl implements RateLimiterService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> script;

    public RateLimiterServiceImpl(StringRedisTemplate redis) {
        this.redis = redis;
        this.script = new DefaultRedisScript<>();
        this.script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/token_bucket.lua")));
        this.script.setResultType(Long.class);
    }

    @Override
    public boolean tryAcquire(String identity, String api, int rate, int capacity) {
        String key = CacheKeyConstants.rateLimitKey(identity, api);
        Long result = redis.execute(script, List.of(key),
                String.valueOf(rate),
                String.valueOf(capacity),
                String.valueOf(System.currentTimeMillis()));
        return result != null && result == 1L;
    }
}
