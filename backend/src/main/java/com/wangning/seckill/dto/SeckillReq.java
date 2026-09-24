package com.wangning.seckill.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

/**
 * 抢座请求。
 */
public record SeckillReq(
        @NotNull(message = "场次ID不能为空") Long scheduleId,
        @NotEmpty(message = "请选择座位") List<@Valid Seat> seats,
        /** 幂等键：前端生成，重复提交用 */
        @NotBlank(message = "requestId不能为空")
        @Pattern(regexp = "^[A-Za-z0-9_-]{8,64}$", message = "requestId格式不合法")
        String requestId
) {
    public record Seat(
            @Min(value = 1, message = "座位行号从1开始") int row,
            @Min(value = 1, message = "座位列号从1开始") int col) {
    }
}
