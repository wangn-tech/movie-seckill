package com.wangning.seckill.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Movie;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.mapper.MovieMapper;
import com.wangning.seckill.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

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

    @Bean
    public ApplicationRunner warmUp() {
        return args -> {
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
            log.info("布隆过滤器灌入场次 ID {} 个，库存已预热", schedules.size());
        };
    }
}
