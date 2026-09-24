package com.wangning.seckill.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Movie;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.entity.SeatLock;
import com.wangning.seckill.mapper.MovieMapper;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mapper.SeatLockMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 启动时预热：
 * 1. 把 DB 里所有 movieId / scheduleId 灌入布隆过滤器
 * 2. 把场次库存预热到 Redis
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class DataWarmUpConfig {

    private final MovieMapper movieMapper;
    private final ScheduleMapper scheduleMapper;
    private final BloomFilterService bloom;
    private final StringRedisTemplate redis;
    private final SeatLockMapper seatLockMapper;

    @Bean
    public ApplicationRunner warmUp() {
        return args -> {
            try {
                // 灌电影 ID
                List<Movie> movies = movieMapper.selectList(new LambdaQueryWrapper<>());
                movies.forEach(m -> bloom.addMovie(m.getId()));
                log.info("布隆过滤器灌入电影 ID {} 个", movies.size());

                // 灌场次 ID + 预热库存
                List<Schedule> schedules = scheduleMapper.selectList(new LambdaQueryWrapper<>());
                schedules.forEach(s -> {
                    bloom.addSchedule(s.getId());
                    redis.opsForValue().set(CacheKeyConstants.stockKey(s.getId()),
                            String.valueOf(s.getAvailableSeats()));
                });
                int projectedSeats = 0;
                for (Schedule schedule : schedules) {
                    List<SeatLock> locks = seatLockMapper.selectActiveBySchedule(schedule.getId());
                    for (SeatLock lock : locks) {
                        String key = CacheKeyConstants.seatKey(schedule.getId(), lock.getRowNum(), lock.getColNum());
                        if (lock.getStatus() == 2) {
                            redis.opsForValue().set(key, "SOLD|" + lock.getOrderNo());
                            projectedSeats++;
                        } else if (lock.getLockUntil() != null && lock.getLockUntil().isAfter(LocalDateTime.now())) {
                            long ttl = Math.max(1, ChronoUnit.SECONDS.between(LocalDateTime.now(), lock.getLockUntil()));
                            redis.opsForValue().set(key, lock.getUserId() + "|" + lock.getLockToken(),
                                    Duration.ofSeconds(ttl));
                            projectedSeats++;
                        }
                    }
                }
                log.info("布隆过滤器灌入场次 ID {} 个，库存和有效座位投影已预热 {} 个",
                        schedules.size(), projectedSeats);
            } catch (Exception e) {
                // 预热失败不阻断启动，数据会在首次访问时加载
                log.warn("启动预热失败（不影响启动）: {}", e.getMessage());
            }
        };
    }
}
