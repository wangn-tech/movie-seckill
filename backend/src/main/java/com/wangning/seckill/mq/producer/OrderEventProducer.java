package com.wangning.seckill.mq.producer;

import com.wangning.seckill.dto.SeckillReq;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 订单事件 Producer：抢座成功后发 MQ，异步落单。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventProducer {

    private final DefaultMQProducer producer;

    @Value("${seckill.mq.topic-order-delay:seckill-order-delay-topic}")
    private String delayTopic;

    /**
     * 发送"订单创建"事件。
     */
    public void sendOrderCreated(String topic, Long userId, Long scheduleId,
                                 List<SeckillReq.Seat> seats, String requestId) {
        String body = String.format(
                "{\"userId\":%d,\"scheduleId\":%d,\"requestId\":\"%s\",\"seats\":%s}",
                userId, scheduleId, requestId, seats.toString());
        try {
            Message msg = new Message(topic, "ORDER_CREATED",
                    requestId, body.getBytes(StandardCharsets.UTF_8));
            producer.send(msg);
            log.info("发送抢座事件成功 requestId={}, topic={}", requestId, topic);
        } catch (Exception e) {
            log.error("发送抢座事件失败 requestId={}", requestId, e);
            // MQ 发送失败不能让用户重试抢座（Redis 已扣库存），
            // Outbox 兜底：本地事务里写 outbox_event，由定时任务重投
            throw new RuntimeException("MQ 发送失败", e);
        }
    }

    /**
     * 发送订单超时关单延迟消息（30 分钟后投递）。
     */
    public void sendOrderTimeout(String orderNo, Long userId, long delayMillis) {
        try {
            Message msg = new Message(delayTopic, "ORDER_TIMEOUT",
                    orderNo,
                    String.format("{\"orderNo\":\"%s\",\"userId\":%d}", orderNo, userId)
                            .getBytes(StandardCharsets.UTF_8));
            // RocketMQ 5: setDelayTimeLevel 不支持任意时长，这里用 deliverTimeMs
            msg.setDelayTimeMs(delayMillis);
            producer.send(msg);
            log.info("发送超时关单消息 orderNo={}", orderNo);
        } catch (Exception e) {
            log.error("发送超时消息失败 orderNo={}", orderNo, e);
        }
    }
}
