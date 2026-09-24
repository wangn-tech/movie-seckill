package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.cache.MultiLevelCacheManager;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.entity.SeatLock;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mapper.SeatLockMapper;
import com.wangning.seckill.service.ScheduleService;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.vo.SeatLayoutVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private final ScheduleMapper scheduleMapper;
    private final MultiLevelCacheManager cache;
    private final BloomFilterService bloom;
    private final SeatLockMapper seatLockMapper;
    private final ObjectMapper objectMapper;

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

    @Override
    public SeatLayoutVO seatLayout(Long scheduleId) {
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场次不存在");
        }

        List<SeatLock> locks = seatLockMapper.selectActiveBySchedule(scheduleId);
        List<SeatLayoutVO.SeatState> lockedSeats = locks.stream()
                .map(lock -> new SeatLayoutVO.SeatState(lock.getRowNum(), lock.getColNum(),
                        lock.getStatus() == 2 ? "SOLD" : "LOCKED"))
                .toList();
        List<SeatLayoutVO.Seat> unavailable = parseUnavailable(schedule.getUnavailableSeats());

        return new SeatLayoutVO(scheduleId, schedule.getSeatRows(), schedule.getSeatCols(),
                schedule.getTotalSeats(), schedule.getAvailableSeats(), schedule.getPrice(),
                unavailable, lockedSeats);
    }

    private List<SeatLayoutVO.Seat> parseUnavailable(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception e) {
            throw new IllegalStateException("场次座位布局配置错误", e);
        }
    }
}
