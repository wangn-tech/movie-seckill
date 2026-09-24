package com.wangning.seckill.dto;

import java.util.List;

public record OrderCreateEvent(
        Long userId,
        Long scheduleId,
        String requestId,
        List<SeckillReq.Seat> seats) {
}
