package com.wangning.seckill.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.common.context.UserContextHolder;
import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.TicketOrderMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "订单")
@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final TicketOrderMapper orderMapper;

    @Operation(summary = "我的订单列表")
    @GetMapping
    public Result<List<TicketOrder>> myOrders() {
        Long userId = UserContextHolder.requireUserId();
        List<TicketOrder> orders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getUserId, userId)
                        .orderByDesc(TicketOrder::getCreateTime));
        return Result.success(orders);
    }

    @Operation(summary = "根据 requestId 查订单（轮询用）")
    @GetMapping("/by-request/{requestId}")
    public Result<TicketOrder> byRequestId(@PathVariable String requestId) {
        Long userId = UserContextHolder.requireUserId();
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getLockToken, requestId)
                        .eq(TicketOrder::getUserId, userId));
        return Result.success(order);
    }
}
