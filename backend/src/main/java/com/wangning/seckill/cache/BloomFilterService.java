package com.wangning.seckill.cache;

import com.wangning.seckill.common.constant.CacheKeyConstants;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

/**
 * 布隆过滤器：拦截不存在的 movieId / scheduleId，防止缓存穿透。
 *
 * <p>应用启动时把 DB 里的合法 ID 全部灌入；后续新增数据时主动 add。
 * 布隆过滤器说"不存在"就一定不存在，直接返回；说"存在"可能误判，再走空值缓存兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BloomFilterService {

    private final RedissonClient redisson;

    private RBloomFilter<Long> movieBloom;
    private RBloomFilter<Long> scheduleBloom;

    @PostConstruct
    public void init() {
        movieBloom = redisson.getBloomFilter(CacheKeyConstants.BLOOM_MOVIE);
        // 预期 1 万元素，误判率 1%
        movieBloom.tryInit(10_000L, 0.01);

        scheduleBloom = redisson.getBloomFilter(CacheKeyConstants.BLOOM_SCHEDULE);
        scheduleBloom.tryInit(10_000L, 0.01);

        log.info("布隆过滤器初始化完成");
    }

    public boolean mightContainMovie(Long id) {
        return movieBloom.contains(id);
    }

    public boolean mightContainSchedule(Long id) {
        return scheduleBloom.contains(id);
    }

    public void addMovie(Long id) {
        movieBloom.add(id);
    }

    public void addSchedule(Long id) {
        scheduleBloom.add(id);
    }
}
