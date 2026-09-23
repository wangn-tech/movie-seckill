package com.wangning.seckill.service;

import com.wangning.seckill.entity.TicketOrder;

import java.util.List;

public interface OrderService {
    List<TicketOrder> myOrders(Long userId);
    TicketOrder getByRequestId(Long userId, String requestId);
}
