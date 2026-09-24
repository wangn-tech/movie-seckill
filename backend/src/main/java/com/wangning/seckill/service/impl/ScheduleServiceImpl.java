package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.cache.MultiLevelCacheManager;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.service.ScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final MultiLevelCacheManager cache;
    private final BloomFilterService bloom;

    @Override
    public List<Schedule> listByMovieAndCinema(Long movieId, Long cinemaId) {
        String key = CacheKeyConstants.scheduleList(movieId, cinemaId);
        return cache.getList(key, Schedule.class, 300, null,
                () -> scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getMovieId, movieId)
                        .eq(Schedule::getCinemaId, cinemaId)
                        .eq(Schedule::getStatus, 1)));
    }

    @Override
    public Schedule detail(Long scheduleId) {
        String key = "schedule:detail:" + scheduleId;
        return cache.get(key, Schedule.class, 300,
                () -> bloom.mightContainSchedule(scheduleId),
                () -> scheduleMapper.selectById(scheduleId));
    }
}
