package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.service.OrderService;
import com.wangning.seckill.vo.OrderVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private final TicketOrderMapper orderMapper;

    @Override
    public List<OrderVO> myOrders(Long userId) {
        return orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .orderByDesc(TicketOrder::getCreateTime)).stream()
                .map(OrderVO::from)
                .toList();
    }

    @Override
    public OrderVO getByRequestId(Long userId, String requestId) {
        return OrderVO.from(orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getLockToken, requestId)
                        .eq(TicketOrder::getUserId, userId)));
    }
}
