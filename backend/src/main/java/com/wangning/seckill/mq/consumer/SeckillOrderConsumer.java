package com.wangning.seckill.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.dto.OrderCreateEvent;
import com.wangning.seckill.entity.OutboxEvent;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.service.OrderCreationService;
import com.wangning.seckill.service.RedisSeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

/**
 * 抢座入口事件消费者。事务由 OrderCreationService 负责，使失败补偿在事务回滚后执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = "${seckill.mq.topic-order}", consumerGroup = "seckill-order-consumer-group")
public class SeckillOrderConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final OrderCreationService orderCreationService;
    private final RedisSeatService redisSeatService;
    private final TicketOrderMapper orderMapper;
    private final OutboxEventMapper outboxMapper;

    @Override
    public void onMessage(String message) {
        OrderCreateEvent event;
        try {
            event = objectMapper.readValue(message, OrderCreateEvent.class);
        } catch (Exception e) {
            log.error("抢座消息格式非法: {}", message, e);
            return;
        }

        try {
            orderCreationService.create(event);
        } catch (DuplicateKeyException e) {
            // 并发重复消息若已有同 requestId 订单，视为幂等成功；否则是座位唯一键冲突。
            Long existing = orderMapper.selectCount(new LambdaQueryWrapper<TicketOrder>()
                    .eq(TicketOrder::getLockToken, event.requestId()));
            if (existing != null && existing > 0) {
                markSucceeded(event.requestId());
                return;
            }
            rejectAndRelease(event, "座位已被其他订单占用");
        } catch (IllegalStateException | IllegalArgumentException e) {
            rejectAndRelease(event, e.getMessage());
        } catch (Exception e) {
            // 数据库/网络等技术异常交给 RocketMQ 重试，不提前释放库存。
            log.error("订单消费技术异常，等待MQ重试 requestId={}", event.requestId(), e);
            throw new RuntimeException(e);
        }
    }

    private void rejectAndRelease(OrderCreateEvent event, String reason) {
        outboxMapper.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventKey, event.requestId())
                .set(OutboxEvent::getProcessStatus, "FAILED")
                .set(OutboxEvent::getFailReason, reason));
        redisSeatService.release(event.userId(), event.scheduleId(), event.requestId(),
                event.seats(), event.seats().size());
        log.warn("订单创建被拒绝，已释放Redis预占 requestId={}, reason={}", event.requestId(), reason);
    }

    private void markSucceeded(String requestId) {
        outboxMapper.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventKey, requestId)
                .set(OutboxEvent::getProcessStatus, "SUCCEEDED"));
    }
}
