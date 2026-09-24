package com.wangning.seckill.controller;

import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.service.ScheduleService;
import com.wangning.seckill.vo.ScheduleVO;
import com.wangning.seckill.vo.SeatLayoutVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "场次")
@RestController
@RequestMapping("/schedules")
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;

    @Operation(summary = "某电影在某影院的场次列表")
    @GetMapping
    public Result<List<ScheduleVO>> list(@RequestParam Long movieId, @RequestParam Long cinemaId) {
        return Result.success(scheduleService.listByMovieAndCinema(movieId, cinemaId).stream()
                .map(ScheduleVO::from).toList());
    }

    @Operation(summary = "场次详情")
    @GetMapping("/{id}")
    public Result<ScheduleVO> detail(@PathVariable Long id) {
        return Result.success(ScheduleVO.from(scheduleService.detail(id)));
    }

    @Operation(summary = "场次座位图：返回每个座位状态")
    @GetMapping("/{id}/seats")
    public Result<SeatLayoutVO> seats(@PathVariable Long id) {
        return Result.success(scheduleService.seatLayout(id));
    }
}
