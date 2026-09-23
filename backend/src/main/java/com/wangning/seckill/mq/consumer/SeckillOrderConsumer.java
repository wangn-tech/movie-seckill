package com.wangning.seckill.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.entity.*;
import com.wangning.seckill.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 抢座消息消费者 — 异步落单。
 *
 * <p>在同一个 DB 事务里完成：
 * <ol>
 *   <li>INSERT ticket_order（uk_lock_token 幂等）</li>
 *   <li>INSERT order_seat 明细</li>
 *   <li>INSERT seat_lock（uk_seat 兜底座位重售）</li>
 *   <li>UPDATE movie_schedule SET available=available-n WHERE available>=n（条件更新兜底超卖）</li>
 *   <li>INSERT outbox_event（下游事件，由 OutboxRelayJob 投递）</li>
 * </ol>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "${seckill.mq.topic-order}", consumerGroup = "seckill-order-consumer-group")
public class SeckillOrderConsumer implements RocketMQListener<String> {

    private final TicketOrderMapper orderMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final SeatLockMapper seatLockMapper;
    private final ScheduleMapper scheduleMapper;
    private final OutboxEventMapper outboxEventMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long userId = node.get("userId").asLong();
            Long scheduleId = node.get("scheduleId").asLong();
            String requestId = node.get("requestId").asText();
            int seatCount = node.get("seats").size();

            doCreateOrder(userId, scheduleId, requestId, seatCount, message);
        } catch (Exception e) {
            log.error("消费抢座消息失败: {}", message, e);
            // RocketMQ 会重试，超过次数进死信队列
            throw new RuntimeException(e);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void doCreateOrder(Long userId, Long scheduleId, String requestId, int seatCount, String rawMsg) {
        // 1. 幂等：根据 lock_token 查是否已有订单
        Long exists = orderMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getLockToken, requestId));
        if (exists != null && exists > 0) {
            log.info("订单已存在，幂等返回 requestId={}", requestId);
            return;
        }

        // 2. 查场次信息
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new IllegalStateException("场次不存在: " + scheduleId);
        }

        // 3. MySQL 条件更新库存：WHERE available >= seatCount 兜底超卖
        int updated = scheduleMapper.update(null,
                new LambdaUpdateWrapper<Schedule>()
                        .setSql("available_seats = available_seats - " + seatCount)
                        .eq(Schedule::getId, scheduleId)
                        .ge(Schedule::getAvailableSeats, seatCount));
        if (updated == 0) {
            // DB 库存不足：回滚，Redis 库存由定时对账任务校准
            log.error("MySQL 库存不足，落单失败 scheduleId={}, need={}", scheduleId, seatCount);
            throw new IllegalStateException("库存不足");
        }

        // 4. 创建订单
        String orderNo = "SO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        TicketOrder order = new TicketOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setScheduleId(scheduleId);
        order.setLockToken(requestId);
        order.setSeatCount(seatCount);
        order.setTotalPrice(schedule.getPrice().multiply(BigDecimal.valueOf(seatCount)));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        orderMapper.insert(order);

        // 5. 写 outbox_event（同事务，保证 DB 订单和事件最终一致）
        OutboxEvent event = new OutboxEvent();
        event.setEventType("ORDER_CREATED");
        event.setTopic("seckill-order-event-topic");
        event.setPayload(rawMsg);
        event.setStatus("PENDING");
        event.setRetryCount(0);
        event.setMaxRetry(10);
        outboxEventMapper.insert(event);

        log.info("订单创建成功 orderNo={}, userId={}, scheduleId={}", orderNo, userId, scheduleId);
    }
}
