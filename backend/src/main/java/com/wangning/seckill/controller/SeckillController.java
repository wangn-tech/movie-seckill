package com.wangning.seckill.controller;

import com.wangning.seckill.common.annotation.RateLimit;
import com.wangning.seckill.common.context.UserContextHolder;
import com.wangning.seckill.common.enums.RateLimitGranularity;
import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.service.SeckillService;
import com.wangning.seckill.vo.SeckillStatusVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "秒杀抢座")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    @Operation(summary = "抢座（核心接口）")
    @PostMapping("/seize")
    @RateLimit(rate = 5, capacity = 10, granularity = RateLimitGranularity.USER)
    public Result<SeckillStatusVO> seize(@Valid @RequestBody SeckillReq req) {
        Long userId = UserContextHolder.requireUserId();
        return Result.success(seckillService.seize(userId, req));
    }

    @Operation(summary = "查询抢座异步处理状态")
    @GetMapping("/requests/{requestId}")
    public Result<SeckillStatusVO> status(@PathVariable String requestId) {
        return Result.success(seckillService.status(UserContextHolder.requireUserId(), requestId));
    }
}
