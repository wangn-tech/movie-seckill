package com.wangning.seckill.cache;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Caffeine L1 → Redis L2 → MySQL 的多级缓存模板。
 *
 * <p>Caffeine 仅保存读多写少的展示数据。Redis 使用逻辑过期保留旧值；发现过期时当前请求
 * 返回旧值，由有界线程池中的任务使用 Redisson 锁完成跨实例单飞重建。
 */
@Slf4j
@Component
public class MultiLevelCacheManager {

    private static final String NULL_FLAG = "__NULL__";
    private static final Duration NULL_TTL = Duration.ofSeconds(30);

    private final Cache<String, Object> localCache;
    private final StringRedisTemplate redis;
    private final RedissonClient redisson;
    private final ObjectMapper objectMapper;
    private final TaskExecutor cacheRebuildExecutor;
    private final ConcurrentHashMap<String, CompletableFuture<Object>> localLoads = new ConcurrentHashMap<>();

    public MultiLevelCacheManager(Cache<String, Object> localCache,
                                  StringRedisTemplate redis,
                                  RedissonClient redisson,
                                  ObjectMapper objectMapper,
                                  @Qualifier("cacheRebuildExecutor") TaskExecutor cacheRebuildExecutor) {
        this.localCache = localCache;
        this.redis = redis;
        this.redisson = redisson;
        this.objectMapper = objectMapper;
        this.cacheRebuildExecutor = cacheRebuildExecutor;
    }

    public <T> T get(String key, Class<T> type, long ttlSec,
                     Supplier<Boolean> bloomCheck, Supplier<T> loader) {
        JavaType javaType = objectMapper.getTypeFactory().constructType(type);
        return get(key, javaType, ttlSec, bloomCheck, loader);
    }

    public <T> List<T> getList(String key, Class<T> elementType, long ttlSec,
                               Supplier<Boolean> bloomCheck, Supplier<List<T>> loader) {
        JavaType javaType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
        return get(key, javaType, ttlSec, bloomCheck, loader);
    }

    @SuppressWarnings("unchecked")
    private <T> T get(String key, JavaType type, long ttlSec,
                      Supplier<Boolean> bloomCheck, Supplier<T> loader) {
        Object l1 = localCache.getIfPresent(key);
        if (l1 != null) {
            return (T) l1;
        }

        CacheObject<T> cached = readRedis(key, type);
        if (cached != null) {
            if (cached.expireAt() < System.currentTimeMillis()) {
                scheduleRebuild(key, type, ttlSec, loader);
            }
            localCache.put(key, cached.data());
            return cached.data();
        }
        if (NULL_FLAG.equals(redis.opsForValue().get(key))) {
            return null;
        }

        if (bloomCheck != null && !bloomCheck.get()) {
            redis.opsForValue().set(key, NULL_FLAG, NULL_TTL);
            return null;
        }

        return loadWithLock(key, type, ttlSec, loader);
    }

    private <T> T loadWithLock(String key, JavaType type, long ttlSec, Supplier<T> loader) {
        RLock lock = redisson.getLock(CacheKeyConstants.rebuildLock(key));
        boolean locked = false;
        try {
            locked = lock.tryLock(2, 15, TimeUnit.SECONDS);
            if (locked) {
                // 获取锁后必须二次检查，其他实例可能已经完成回填。
                CacheObject<T> latest = readRedis(key, type);
                if (latest != null) {
                    localCache.put(key, latest.data());
                    return latest.data();
                }
                T data = loader.get();
                put(key, data, ttlSec);
                return data;
            }

            Thread.sleep(50);
            CacheObject<T> latest = readRedis(key, type);
            return latest != null ? latest.data() : loadWithSingleFlight(key, ttlSec, loader);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return loadWithSingleFlight(key, ttlSec, loader);
        } catch (Exception e) {
            log.error("缓存回源失败 key={}", key, e);
            return loadWithSingleFlight(key, ttlSec, loader);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T loadWithSingleFlight(String key, long ttlSec, Supplier<T> loader) {
        CompletableFuture<Object> created = new CompletableFuture<>();
        CompletableFuture<Object> active = localLoads.putIfAbsent(key, created);
        if (active != null) {
            try {
                return (T) active.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                throw e;
            }
        }

        try {
            T data = loader.get();
            put(key, data, ttlSec);
            created.complete(data);
            return data;
        } catch (RuntimeException e) {
            created.completeExceptionally(e);
            throw e;
        } finally {
            localLoads.remove(key, created);
        }
    }

    public <T> void put(String key, T data, long ttlSec) {
        try {
            if (data == null) {
                redis.opsForValue().set(key, NULL_FLAG, NULL_TTL);
                return;
            }
            CacheObject<T> value = new CacheObject<>(data, System.currentTimeMillis() + ttlSec * 1000);
            // 物理 TTL 始终晚于逻辑 TTL，为异步重建保留可返回的旧值。
            long physicalTtl = ttlSec + 60 + ThreadLocalRandom.current().nextLong(300);
            redis.opsForValue().set(key, objectMapper.writeValueAsString(value), Duration.ofSeconds(physicalTtl));
            localCache.put(key, data);
        } catch (Exception e) {
            log.error("写缓存失败 key={}", key, e);
        }
    }

    private <T> void scheduleRebuild(String key, JavaType type, long ttlSec, Supplier<T> loader) {
        try {
            cacheRebuildExecutor.execute(() -> rebuild(key, type, ttlSec, loader));
        } catch (RuntimeException e) {
            log.warn("缓存重建队列已满 key={}", key, e);
        }
    }

    private <T> void rebuild(String key, JavaType type, long ttlSec, Supplier<T> loader) {
        RLock lock = redisson.getLock(CacheKeyConstants.rebuildLock(key));
        boolean locked = false;
        try {
            locked = lock.tryLock(0, 15, TimeUnit.SECONDS);
            if (!locked) {
                return;
            }

            CacheObject<T> latest = readRedis(key, type);
            if (latest != null && latest.expireAt() >= System.currentTimeMillis()) {
                localCache.put(key, latest.data());
                return;
            }

            put(key, loader.get(), ttlSec);
            log.info("缓存异步重建完成 key={}", key);
        } catch (Exception e) {
            log.error("缓存异步重建失败 key={}", key, e);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private <T> CacheObject<T> readRedis(String key, JavaType valueType) {
        String json = redis.opsForValue().get(key);
        if (json == null || NULL_FLAG.equals(json)) {
            return null;
        }
        try {
            JavaType wrapperType = objectMapper.getTypeFactory()
                    .constructParametricType(CacheObject.class, valueType);
            return objectMapper.readValue(json, wrapperType);
        } catch (Exception e) {
            log.warn("缓存反序列化失败，删除坏数据 key={}", key, e);
            redis.delete(key);
            return null;
        }
    }

    public void evict(String key) {
        redis.delete(key);
        localCache.invalidate(key);
    }

    public record CacheObject<T>(T data, long expireAt) {
    }
}
