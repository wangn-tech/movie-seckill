package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final TicketOrderMapper orderMapper;

    @Override
    public List<TicketOrder> myOrders(Long userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .orderByDesc(TicketOrder::getCreateTime));
    }

    @Override
    public TicketOrder getByRequestId(Long userId, String requestId) {
        return orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getLockToken, requestId)
                        .eq(TicketOrder::getUserId, userId));
    }
}
