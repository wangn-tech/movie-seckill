package com.wangning.seckill.job;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 订单超时关单兜底：扫 status=0 且已过期的订单，CAS 关单并返还 Redis 库存。
 *
 * <p>RocketMQ 定时消息是第一道关单，这个任务是第二道兜底。
 */
@Slf4j
@Component
public class OrderTimeoutJob {

    private final TicketOrderMapper orderMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final StringRedisTemplate redis;

    private final DefaultRedisScript<Long> releaseScript;

    public OrderTimeoutJob(TicketOrderMapper orderMapper,
                           OrderSeatMapper orderSeatMapper,
                           StringRedisTemplate redis) {
        this.orderMapper = orderMapper;
        this.orderSeatMapper = orderSeatMapper;
        this.redis = redis;
        this.releaseScript = new DefaultRedisScript<>();
        this.releaseScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seat_release.lua")));
        this.releaseScript.setResultType(Long.class);
    }

    /** 每分钟扫一次 */
    @Scheduled(fixedDelay = 60_000)
    public void closeTimeoutOrders() {
        List<TicketOrder> timeoutOrders = orderMapper.selectList(
                new LambdaQueryWrapper<TicketOrder>()
                        .eq(TicketOrder::getStatus, 0)
                        .lt(TicketOrder::getExpireTime, LocalDateTime.now())
                        .last("LIMIT 20"));

        for (TicketOrder order : timeoutOrders) {
            // CAS 关单：只有 status=0 才能改成 2
            int updated = orderMapper.update(null,
                    new LambdaUpdateWrapper<TicketOrder>()
                            .eq(TicketOrder::getId, order.getId())
                            .eq(TicketOrder::getStatus, 0)
                            .set(TicketOrder::getStatus, 2));
            if (updated == 0) {
                continue; // 已被其他线程处理
            }

            // Lua 返还 Redis 库存（一次性凭证防重复返还）
            List<OrderSeat> seats = orderSeatMapper.selectList(
                    new LambdaQueryWrapper<OrderSeat>().eq(OrderSeat::getOrderId, order.getId()));

            List<String> keys = new ArrayList<>();
            keys.add(CacheKeyConstants.stockKey(order.getScheduleId()));
            keys.add(CacheKeyConstants.reservationKey(order.getLockToken()));
            for (OrderSeat s : seats) {
                keys.add(CacheKeyConstants.seatKey(order.getScheduleId(), s.getRowNum(), s.getColNum()));
            }

            redis.execute(releaseScript, keys,
                    String.valueOf(order.getUserId()),
                    order.getLockToken(),
                    String.valueOf(order.getSeatCount()));

            log.info("超时关单 orderNo={}, 已返还库存", order.getOrderNo());
        }
    }
}
