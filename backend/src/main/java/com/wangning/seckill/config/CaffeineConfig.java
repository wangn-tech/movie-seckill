package com.wangning.seckill.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine 本地缓存（L1）配置。
 *
 * <p>单实例内热点数据缓存，QPS 极高时挡住 Redis 往返。
 * 注意：多实例部署时 L1 不共享，靠 Redis L2 保持一致；失效通过消息通知或短 TTL 兜底。
 */
@Configuration
public class CaffeineConfig {

    /** 热点数据 L1：电影详情、场次库存展示等 */
    @Bean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(new Expiry<String, Object>() {
                    @Override
                    public long expireAfterCreate(String key, Object value, long currentTime) {
                        return ttlNanos(key);
                    }

                    @Override
                    public long expireAfterUpdate(String key, Object value, long currentTime, long currentDuration) {
                        return ttlNanos(key);
                    }

                    @Override
                    public long expireAfterRead(String key, Object value, long currentTime, long currentDuration) {
                        return currentDuration;
                    }

                    private long ttlNanos(String key) {
                        long seconds = key.startsWith("schedule:") ? 30 : 120;
                        return TimeUnit.SECONDS.toNanos(seconds);
                    }
                })
                .recordStats()
                .build();
    }
}
