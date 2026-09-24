package com.wangning.seckill.mq.consumer;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.dto.InventoryEvent;
import com.wangning.seckill.entity.OutboxEvent;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.service.RedisSeatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rocketmq.consumer.enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(topic = "${seckill.mq.topic-inventory:seckill-inventory-topic}",
        consumerGroup = "seckill-inventory-consumer-group")
public class InventoryEventConsumer implements RocketMQListener<String> {

    private final ObjectMapper objectMapper;
    private final RedisSeatService redisSeatService;
    private final OutboxEventMapper outboxMapper;

    @Override
    public void onMessage(String message) {
        try {
            InventoryEvent event = objectMapper.readValue(message, InventoryEvent.class);
            // MQ 至少一次投递，CONFIRM/RELEASE 脚本必须依赖 event_key、requestId 和 marker 保证重复消费安全。
            if ("CONFIRM".equals(event.action())) {
                redisSeatService.confirm(event.userId(), event.scheduleId(), event.requestId(),
                        event.orderNo(), event.seats());
            } else if ("RELEASE".equals(event.action())) {
                redisSeatService.release(event.userId(), event.scheduleId(), event.requestId(),
                        event.seats(), event.seatCount());
            } else {
                throw new IllegalArgumentException("未知库存事件: " + event.action());
            }
            // 只有 Redis 补偿成功后才推进 Outbox 处理状态，失败抛异常交给 RocketMQ 重试。
            outboxMapper.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                    .eq(OutboxEvent::getEventKey, event.eventKey())
                    .set(OutboxEvent::getProcessStatus, "SUCCEEDED")
                    .set(OutboxEvent::getFailReason, null));
        } catch (Exception e) {
            log.error("处理库存事件失败: {}", message, e);
            throw new RuntimeException(e);
        }
    }
}
