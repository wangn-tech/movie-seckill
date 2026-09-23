package com.wangning.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.cache.MultiLevelCacheManager;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.mapper.ScheduleMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final MultiLevelCacheManager cache;
    private final BloomFilterService bloom;

    /** 某电影在某影院的场次列表 */
    public List<Schedule> listByMovieAndCinema(Long movieId, Long cinemaId) {
        String key = CacheKeyConstants.scheduleList(movieId, cinemaId);
        return cache.get(key, List.class, 300, null,
                () -> scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                        .eq(Schedule::getMovieId, movieId)
                        .eq(Schedule::getCinemaId, cinemaId)
                        .eq(Schedule::getStatus, 1)));
    }

    /** 场次详情 */
    public Schedule detail(Long scheduleId) {
        String key = "schedule:detail:" + scheduleId;
        return cache.get(key, Schedule.class, 300,
                () -> bloom.mightContainSchedule(scheduleId),
                () -> scheduleMapper.selectById(scheduleId));
    }
}
