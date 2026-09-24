package com.wangning.seckill.dto;

import java.util.List;

public record InventoryEvent(
        String eventKey,
        String action,
        Long userId,
        Long scheduleId,
        String requestId,
        String orderNo,
        int seatCount,
        List<SeckillReq.Seat> seats) {
}
