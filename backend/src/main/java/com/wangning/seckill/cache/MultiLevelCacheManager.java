package com.wangning.seckill.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 多级缓存管理器 — 面试核心。
 *
 * <p>读路径：Caffeine(L1) → Redis(L2) → DB(回源)。
 *
 * <p>解决三大问题：
 * <ul>
 *   <li>缓存穿透：布隆过滤器 + 空值缓存（调用方在 loader 返回 null 时由本类写空值短 TTL）</li>
 *   <li>缓存击穿：热点 key 逻辑过期，发现过期不阻塞请求，先返回旧值，异步抢 Redisson 锁重建</li>
 *   <li>缓存雪崩：Redis TTL 加随机偏移；L1 Caffeine 兜底</li>
 * </ul>
 *
 * @param <T> 缓存值类型
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MultiLevelCacheManager {

    private final Cache<String, Object> localCache;
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;

    /** 空值标记，TTL 短 */
    private static final String NULL_FLAG = "__NULL__";

    /**
     * 模板方法：按 key 查多级缓存。
     *
     * @param key      Redis key
     * @param type     返回类型
     * @param ttlSec   Redis 逻辑过期时间（秒）
     * @param bloomCheck 布隆过滤器检查（返回 false 表示一定不存在，直接返回 null）
     * @param loader   DB 回源
     */
    public <T> T get(String key, Class<T> type, long ttlSec,
                     Supplier<Boolean> bloomCheck, Supplier<T> loader) {
        // 1. L1 Caffeine
        Object l1 = localCache.getIfPresent(key);
        if (l1 != null) {
            return type.cast(l1);
        }

        // 2. L2 Redis
        String json = redis.opsForValue().get(key);
        if (json != null) {
            if (NULL_FLAG.equals(json)) {
                return null; // 空值缓存命中，防止穿透
            }
            // 检查逻辑过期
            CacheObject<T> co;
            try {
                co = objectMapper.readValue(json, objectMapper.getTypeFactory().constructParametricType(CacheObject.class, type));
            } catch (Exception e) {
                log.warn("缓存反序列化失败 key={}", key, e);
                redis.delete(key);
                return null;
            }
            if (co.expireAt < System.currentTimeMillis()) {
                // 逻辑过期：异步重建，当前线程返回旧值（不阻塞）
                asyncRebuild(key, type, ttlSec, loader);
            }
            // 回填 L1
            localCache.put(key, co.data);
            return co.data;
        }

        // 3. 布隆过滤器：明确不存在，直接写空值返回
        if (bloomCheck != null && !bloomCheck.get()) {
            redis.opsForValue().set(key, NULL_FLAG, Duration.ofSeconds(30));
            return null;
        }

        // 4. 回源 DB：抢 Redisson 分布式锁，只让一个线程回源
        RLock lock = redisson.getLock(CacheKeyConstants.rebuildLock(key));
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (locked) {
                T data = loader.get();
                put(key, data, ttlSec, type);
                return data;
            } else {
                // 没抢到锁，短暂等待后重读 Redis（其他线程可能已重建）
                Thread.sleep(50);
                json = redis.opsForValue().get(key);
                if (json != null && !NULL_FLAG.equals(json)) {
                    CacheObject<T> co = objectMapper.readValue(json, objectMapper.getTypeFactory().constructParametricType(CacheObject.class, type));
                    localCache.put(key, co.data);
                    return co.data;
                }
                // 重读仍没有，直接回源（兜底，不阻塞业务）
                return loader.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loader.get();
        } catch (Exception e) {
            log.error("回源异常 key={}", key, e);
            return loader.get();
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 写缓存：带逻辑过期时间，TTL 加随机偏移防雪崩。
     */
    public <T> void put(String key, T data, long ttlSec, Class<T> type) {
        try {
            if (data == null) {
                redis.opsForValue().set(key, NULL_FLAG, Duration.ofSeconds(30));
                return;
            }
            long now = System.currentTimeMillis();
            long expireAt = now + ttlSec * 1000;
            CacheObject<T> co = new CacheObject<>(data, expireAt);
            // TTL 加随机 0~5 分钟偏移，打散集中失效
            long randomTtl = ttlSec + (long) (Math.random() * 300);
            redis.opsForValue().set(key, objectMapper.writeValueAsString(co), Duration.ofSeconds(randomTtl));
            localCache.put(key, data);
        } catch (Exception e) {
            log.error("写缓存失败 key={}", key, e);
        }
    }

    /**
     * 异步重建缓存：抢到锁的线程负责回源，其他请求读旧值。
     */
    @Async
    public <T> void asyncRebuild(String key, Class<T> type, long ttlSec, Supplier<T> loader) {
        RLock lock = redisson.getLock(CacheKeyConstants.rebuildLock(key));
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 5, TimeUnit.SECONDS);
            if (locked) {
                T data = loader.get();
                put(key, data, ttlSec, type);
                log.info("异步缓存重建成功 key={}", key);
            }
        } catch (Exception e) {
            log.error("异步重建失败 key={}", key, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 删除缓存（写操作后调用） */
    public void evict(String key) {
        redis.delete(key);
        localCache.invalidate(key);
    }

    /**
     * 缓存值包装：带逻辑过期时间戳。
     */
    public record CacheObject<T>(T data, long expireAt) {
    }
}
