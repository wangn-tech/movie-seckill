package com.wangning.seckill.service;

/**
 * 模拟支付服务接口。
 */
public interface PaymentService {

    /**
     * 支付订单：余额扣减 → 订单状态 CAS → seat_lock 已售。
     */
    void pay(Long userId, String orderNo);
}
