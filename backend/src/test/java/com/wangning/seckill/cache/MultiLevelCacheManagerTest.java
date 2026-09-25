package com.wangning.seckill.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.wangning.seckill.entity.Movie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class MultiLevelCacheManagerTest {

    private Cache<String, Object> localCache;
    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private RedissonClient redisson;
    private MultiLevelCacheManager manager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        localCache = mock(Cache.class);
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        redisson = mock(RedissonClient.class);
        when(redis.opsForValue()).thenReturn(values);
        manager = new MultiLevelCacheManager(
                localCache,
                redis,
                redisson,
                new ObjectMapper(),
                mock(TaskExecutor.class));
    }

    @Test
    void returnsL1ValueWithoutRedisOrDatabase() {
        Movie movie = new Movie();
        movie.setId(1L);
        when(localCache.getIfPresent("movie:detail:1")).thenReturn(movie);
        AtomicBoolean loaded = new AtomicBoolean(false);

        Movie result = manager.get("movie:detail:1", Movie.class, 600, null, () -> {
            loaded.set(true);
            return null;
        });

        assertThat(result).isSameAs(movie);
        assertThat(loaded).isFalse();
        verifyNoInteractions(redis);
    }

    @Test
    void deserializesTypedListFromRedisAndBackfillsL1() throws Exception {
        Movie movie = new Movie();
        movie.setId(7L);
        movie.setName("缓存电影");
        String json = new ObjectMapper().writeValueAsString(
                new MultiLevelCacheManager.CacheObject<>(List.of(movie), System.currentTimeMillis() + 60_000));
        when(values.get("movie:list:1")).thenReturn(json);

        List<Movie> result = manager.getList("movie:list:1", Movie.class, 600, null, List::of);

        assertThat(result).singleElement()
                .isInstanceOf(Movie.class)
                .extracting(Movie::getName)
                .isEqualTo("缓存电影");
        verify(localCache).put("movie:list:1", result);
    }

    @Test
    void bloomNegativeSkipsDatabaseAndWritesShortNullCache() {
        when(values.get("movie:detail:999")).thenReturn(null);
        AtomicBoolean loaded = new AtomicBoolean(false);

        Movie result = manager.get("movie:detail:999", Movie.class, 600,
                () -> false,
                () -> {
                    loaded.set(true);
                    return new Movie();
                });

        assertThat(result).isNull();
        assertThat(loaded).isFalse();
        verify(values).set(eq("movie:detail:999"), eq("__NULL__"), any());
    }

    @Test
    void returnsNullCacheWithOneRedisRead() {
        when(values.get("movie:detail:999")).thenReturn("__NULL__");
        AtomicBoolean loaded = new AtomicBoolean(false);

        Movie result = manager.get("movie:detail:999", Movie.class, 600,
                () -> true,
                () -> {
                    loaded.set(true);
                    return new Movie();
                });

        assertThat(result).isNull();
        assertThat(loaded).isFalse();
        verify(values, times(1)).get("movie:detail:999");
    }

    @Test
    void lockContentionUsesOneLocalDatabaseLoad() throws Exception {
        var lock = mock(org.redisson.api.RLock.class);
        when(lock.tryLock(2, 15, TimeUnit.SECONDS)).thenReturn(false);
        when(redisson.getLock(anyString())).thenReturn(lock);
        when(values.get("movie:detail:8")).thenReturn(null);
        AtomicInteger loads = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch loaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLoader = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Movie> task = () -> manager.get("movie:detail:8", Movie.class, 600, null, () -> {
                loads.incrementAndGet();
                loaderStarted.countDown();
                try {
                    releaseLoader.await(1, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return new Movie();
            });
            java.util.concurrent.Callable<Movie> gatedTask = () -> {
                ready.countDown();
                ready.await(1, TimeUnit.SECONDS);
                return task.call();
            };
            var first = pool.submit(gatedTask);
            var second = pool.submit(gatedTask);
            ready.await(1, TimeUnit.SECONDS);
            loaderStarted.await(1, TimeUnit.SECONDS);
            releaseLoader.countDown();
            first.get(2, TimeUnit.SECONDS);
            second.get(2, TimeUnit.SECONDS);
            assertThat(loads).hasValue(1);
        } finally {
            pool.shutdownNow();
        }
    }
}
