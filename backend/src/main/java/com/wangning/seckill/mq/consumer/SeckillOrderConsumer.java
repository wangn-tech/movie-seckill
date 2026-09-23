package com.wangning.seckill.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import java.util.ArrayList;
import java.util.List;
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
 *   <li>INSERT outbox_event（下游事件）</li>
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
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            Long userId = node.get("userId").asLong();
            Long scheduleId = node.get("scheduleId").asLong();
            String requestId = node.get("requestId").asText();

            List<int[]> seats = new ArrayList<>();
            node.withArray("seats").forEach(s -> seats.add(new int[]{s.get("row").asInt(), s.get("col").asInt()}));

            doCreateOrder(userId, scheduleId, requestId, seats, message);
        } catch (Exception e) {
            log.error("消费抢座消息失败: {}", message, e);
            throw new RuntimeException(e);
        }
    }

    public void doCreateOrder(Long userId, Long scheduleId, String requestId,
                              List<int[]> seats, String rawMsg) {
        // 1. 幂等
        Long exists = orderMapper.selectCount(
                new LambdaQueryWrapper<TicketOrder>().eq(TicketOrder::getLockToken, requestId));
        if (exists != null && exists > 0) {
            log.info("订单已存在，幂等返回 requestId={}", requestId);
            return;
        }

        // 2. 查场次 + 电影 + 影院（填冗余字段）
        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new IllegalStateException("场次不存在: " + scheduleId);
        }
        Movie movie = movieMapper.selectById(schedule.getMovieId());
        Cinema cinema = cinemaMapper.selectById(schedule.getCinemaId());

        // 3. MySQL 条件更新库存
        int seatCount = seats.size();
        int updated = scheduleMapper.update(null,
                new LambdaUpdateWrapper<Schedule>()
                        .setSql("available_seats = available_seats - " + seatCount)
                        .eq(Schedule::getId, scheduleId)
                        .ge(Schedule::getAvailableSeats, seatCount));
        if (updated == 0) {
            throw new IllegalStateException("库存不足");
        }

        // 4. 创建订单
        String orderNo = "SO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        StringBuilder seatsInfo = new StringBuilder();
        TicketOrder order = new TicketOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setScheduleId(scheduleId);
        order.setLockToken(requestId);
        order.setMovieName(movie != null ? movie.getName() : "");
        order.setCinemaName(cinema != null ? cinema.getName() : "");
        order.setShowTime(schedule.getShowDate() + " " + schedule.getShowTime());
        order.setSeatCount(seatCount);
        order.setTotalPrice(schedule.getPrice().multiply(BigDecimal.valueOf(seatCount)));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));

        for (int i = 0; i < seats.size(); i++) {
            int[] s = seats.get(i);
            if (i > 0) seatsInfo.append(",");
            seatsInfo.append(s[0] + 1).append("排").append(s[1] + 1).append("座");
        }
        order.setSeatsInfo(seatsInfo.toString());
        orderMapper.insert(order);

        // 5. 写 order_seat 明细 + seat_lock
        for (int[] s : seats) {
            OrderSeat os = new OrderSeat();
            os.setOrderId(order.getId());
            os.setOrderNo(orderNo);
            os.setScheduleId(scheduleId);
            os.setRowNum(s[0]);
            os.setColNum(s[1]);
            orderSeatMapper.insert(os);

            SeatLock sl = new SeatLock();
            sl.setScheduleId(scheduleId);
            sl.setRowNum(s[0]);
            sl.setColNum(s[1]);
            sl.setUserId(userId);
            sl.setLockToken(requestId);
            sl.setOrderNo(orderNo);
            sl.setLockUntil(LocalDateTime.now().plusMinutes(30));
            sl.setStatus(1);
            seatLockMapper.insert(sl);
        }

        // 6. 写 outbox_event
        OutboxEvent event = new OutboxEvent();
        event.setEventType("ORDER_CREATED");
        event.setTopic("seckill-order-event-topic");
        event.setPayload(rawMsg);
        event.setStatus("PENDING");
        event.setRetryCount(0);
        event.setMaxRetry(10);
        outboxEventMapper.insert(event);

        log.info("订单创建成功 orderNo={}, userId={}, seats={}", orderNo, userId, seatsInfo);
    }
}
