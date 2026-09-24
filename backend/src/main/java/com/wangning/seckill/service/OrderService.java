package com.wangning.seckill.service;

import com.wangning.seckill.vo.OrderVO;

import java.util.List;

public interface OrderService {
    List<OrderVO> myOrders(Long userId);
    OrderVO getByRequestId(Long userId, String requestId);
}
