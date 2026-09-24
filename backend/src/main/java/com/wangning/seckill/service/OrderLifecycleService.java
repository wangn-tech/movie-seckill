package com.wangning.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.dto.InventoryEvent;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.entity.*;
import com.wangning.seckill.mapper.OrderSeatMapper;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mapper.SeatLockMapper;
import com.wangning.seckill.mapper.TicketOrderMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderLifecycleService {

    private final TicketOrderMapper orderMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final SeatLockMapper seatLockMapper;
    private final ScheduleMapper scheduleMapper;
    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Value("${seckill.mq.topic-inventory:seckill-inventory-topic}")
    private String inventoryTopic;

    @Transactional(rollbackFor = Exception.class)
    public boolean cancelExpired(Long orderId) {
        // 先用订单状态 CAS 抢取消权；定时任务重复扫描或并发支付都只能有一个请求进入补偿。
        TicketOrder order = orderMapper.selectById(orderId);
        if (order == null || order.getStatus() != 0) {
            return false;
        }

        int updated = orderMapper.update(null, new LambdaUpdateWrapper<TicketOrder>()
                .eq(TicketOrder::getId, orderId)
                .eq(TicketOrder::getStatus, 0)
                .set(TicketOrder::getStatus, 2));
        if (updated == 0) {
            return false;
        }

        // MySQL 库存是最终事实，Redis 释放放入 Outbox，避免 DB 已提交而 Redis 未恢复。
        scheduleMapper.update(null, new LambdaUpdateWrapper<Schedule>()
                .eq(Schedule::getId, order.getScheduleId())
                .setSql("available_seats = available_seats + " + order.getSeatCount()));
        seatLockMapper.update(null, new LambdaUpdateWrapper<SeatLock>()
                .eq(SeatLock::getScheduleId, order.getScheduleId())
                .eq(SeatLock::getOrderNo, order.getOrderNo())
                .eq(SeatLock::getStatus, 1)
                .set(SeatLock::getStatus, 0));

        List<OrderSeat> orderSeats = orderSeatMapper.selectList(
                new LambdaQueryWrapper<OrderSeat>().eq(OrderSeat::getOrderId, orderId));
        List<SeckillReq.Seat> seats = orderSeats.stream()
                .map(seat -> new SeckillReq.Seat(seat.getRowNum(), seat.getColNum()))
                .toList();
        String eventKey = "cancel:" + order.getOrderNo();

        try {
            OutboxEvent event = new OutboxEvent();
            event.setEventKey(eventKey);
            event.setUserId(order.getUserId());
            event.setScheduleId(order.getScheduleId());
            event.setEventType("SEAT_RELEASE");
            event.setTopic(inventoryTopic);
            event.setPayload(objectMapper.writeValueAsString(new InventoryEvent(
                    eventKey, "RELEASE", order.getUserId(), order.getScheduleId(), order.getLockToken(),
                    order.getOrderNo(), order.getSeatCount(), seats)));
            event.setStatus("PENDING");
            event.setProcessStatus("PROCESSING");
            event.setRetryCount(0);
            event.setMaxRetry(10);
            outboxMapper.insert(event);
        } catch (Exception e) {
            throw new IllegalStateException("创建关单补偿事件失败", e);
        }

        log.info("超时订单已关闭并恢复MySQL库存 orderNo={}", order.getOrderNo());
        return true;
    }
}
