package com.wangning.seckill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.entity.SeatLock;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mapper.SeatLockMapper;
import com.wangning.seckill.service.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "场次")
@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final ScheduleMapper scheduleMapper;
    private final SeatLockMapper seatLockMapper;

    @Operation(summary = "某电影在某影院的场次列表")
    @GetMapping
    public Result<List<Schedule>> list(@RequestParam Long movieId, @RequestParam Long cinemaId) {
        return Result.success(scheduleService.listByMovieAndCinema(movieId, cinemaId));
    }

    @Operation(summary = "场次详情")
    @GetMapping("/{id}")
    public Result<Schedule> detail(@PathVariable Long id) {
        return Result.success(scheduleService.detail(id));
    }

    @Operation(summary = "场次座位图：返回每个座位状态")
    @GetMapping("/{id}/seats")
    public Result<Map<String, Object>> seats(@PathVariable Long id) {
        Schedule s = scheduleMapper.selectById(id);
        int rows = s != null && s.getHallName() != null ? 8 : 8;
        int cols = 10;

        // 查所有未释放的座位锁（锁定中+已售）
        List<SeatLock> locks = seatLockMapper.selectList(
                new LambdaQueryWrapper<SeatLock>()
                        .eq(SeatLock::getScheduleId, id)
                        .ne(SeatLock::getStatus, 0));

        // 已锁座位集合
        List<Map<String, Object>> lockedSeats = new ArrayList<>();
        for (SeatLock sl : locks) {
            Map<String, Object> m = new HashMap<>();
            m.put("row", sl.getRowNum());
            m.put("col", sl.getColNum());
            m.put("status", sl.getStatus() == 2 ? "sold" : "locked");
            lockedSeats.add(m);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("rows", rows);
        result.put("cols", cols);
        result.put("totalSeats", s != null ? s.getTotalSeats() : rows * cols);
        result.put("availableSeats", s != null ? s.getAvailableSeats() : 0);
        result.put("lockedSeats", lockedSeats);
        return Result.success(result);
    }
}
