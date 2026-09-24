package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.entity.SeatLock;
import com.wangning.seckill.entity.OrderSeat;
import com.wangning.seckill.entity.OutboxEvent;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.entity.User;
import com.wangning.seckill.mapper.SeatLockMapper;
import com.wangning.seckill.mapper.OrderSeatMapper;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.mapper.UserMapper;
import com.wangning.seckill.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.dto.InventoryEvent;
import com.wangning.seckill.dto.SeckillReq;
import org.springframework.beans.factory.annotation.Value;

/**
 * 模拟支付服务实现。
 *
 * <p>支付链路：余额扣减(CAS) → 订单状态 CAS 0→1 → seat_lock 1→2 已售。
 * 全部在一个事务里，失败回滚。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final TicketOrderMapper orderMapper;
    private final UserMapper userMapper;
    private final SeatLockMapper seatLockMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final OutboxEventMapper outboxMapper;
    private final ObjectMapper objectMapper;

    @Value("${seckill.mq.topic-inventory:seckill-inventory-topic}")
    private String inventoryTopic;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pay(Long userId, String orderNo) {
        // 1. 查订单
        TicketOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getOrderNo, orderNo)
                        .eq(TicketOrder::getUserId, userId));
        if (order == null) {
            throw new BizException(ResultCode.ORDER_NOT_FOUND);
        }
        if (order.getStatus() != 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
        }
        if (order.getExpireTime() == null || order.getExpireTime().isBefore(LocalDateTime.now())) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL, "订单已超时，请重新选座");
        }

        // 2. CAS 扣余额（元转分，四舍五入，避免精度丢失）
        int cents = order.getTotalPrice()
                .multiply(BigDecimal.valueOf(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .intValueExact();
        int balanceUpdated = userMapper.update(null,
                new LambdaUpdateWrapper<User>()
                        .setSql("balance = balance - " + cents)
                        .eq(User::getId, userId)
                        .ge(User::getBalance, cents));
        if (balanceUpdated == 0) {
            throw new BizException(ResultCode.BALANCE_NOT_ENOUGH);
        }

        // 3. CAS 更新订单状态：0→1
        int orderUpdated = orderMapper.update(null,
                new LambdaUpdateWrapper<TicketOrder>()
                        .eq(TicketOrder::getId, order.getId())
                        .eq(TicketOrder::getStatus, 0)
                        .set(TicketOrder::getStatus, 1)
                        .set(TicketOrder::getPayTime, LocalDateTime.now()));
        if (orderUpdated == 0) {
            throw new BizException(ResultCode.ORDER_STATUS_ILLEGAL);
        }

        // 4. seat_lock 标记已售
        seatLockMapper.update(null,
                new LambdaUpdateWrapper<SeatLock>()
                        .eq(SeatLock::getScheduleId, order.getScheduleId())
                        .eq(SeatLock::getOrderNo, orderNo)
                        .set(SeatLock::getStatus, 2));

        List<OrderSeat> orderSeats = orderSeatMapper.selectList(
                new LambdaQueryWrapper<OrderSeat>().eq(OrderSeat::getOrderId, order.getId()));
        List<SeckillReq.Seat> seats = orderSeats.stream()
                .map(seat -> new SeckillReq.Seat(seat.getRowNum(), seat.getColNum()))
                .toList();
        String eventKey = "pay:" + orderNo;
        try {
            OutboxEvent event = new OutboxEvent();
            event.setEventKey(eventKey);
            event.setUserId(userId);
            event.setScheduleId(order.getScheduleId());
            event.setEventType("SEAT_CONFIRM");
            event.setTopic(inventoryTopic);
            event.setPayload(objectMapper.writeValueAsString(new InventoryEvent(
                    eventKey, "CONFIRM", userId, order.getScheduleId(), order.getLockToken(),
                    orderNo, order.getSeatCount(), seats)));
            event.setStatus("PENDING");
            event.setProcessStatus("PROCESSING");
            event.setRetryCount(0);
            event.setMaxRetry(10);
            outboxMapper.insert(event);
        } catch (Exception e) {
            throw new IllegalStateException("创建支付库存事件失败", e);
        }

        log.info("支付成功 orderNo={}, userId={}", orderNo, userId);
    }
}
