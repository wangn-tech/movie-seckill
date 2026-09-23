package com.wangning.seckill.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * 抢座请求。
 */
public record SeckillReq(
        @NotNull(message = "场次ID不能为空") Long scheduleId,
        @NotEmpty(message = "请选择座位") List<Seat> seats,
        /** 幂等键：前端生成，重复提交用 */
        String requestId
) {
    public record Seat(int row, int col) {
    }
}
