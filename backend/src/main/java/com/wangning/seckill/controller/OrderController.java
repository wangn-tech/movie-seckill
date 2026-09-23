package com.wangning.seckill.controller;

import com.wangning.seckill.common.context.UserContextHolder;
import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.service.OrderService;
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

    private final OrderService orderService;

    @Operation(summary = "我的订单列表")
    @GetMapping
    public Result<List<TicketOrder>> myOrders() {
        return Result.success(orderService.myOrders(UserContextHolder.requireUserId()));
    }

    @Operation(summary = "根据 requestId 查订单（轮询用）")
    @GetMapping("/by-request/{requestId}")
    public Result<TicketOrder> byRequestId(@PathVariable String requestId) {
        return Result.success(orderService.getByRequestId(UserContextHolder.requireUserId(), requestId));
    }
}
