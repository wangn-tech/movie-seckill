package com.wangning.seckill.vo;

public record SeckillStatusVO(
        String requestId,
        String status,
        String message,
        OrderVO order) {

    public static SeckillStatusVO processing(String requestId) {
        return new SeckillStatusVO(requestId, "PROCESSING", "订单正在创建", null);
    }
}
