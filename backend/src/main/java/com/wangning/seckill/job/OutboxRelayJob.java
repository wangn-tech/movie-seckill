package com.wangning.seckill.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wangning.seckill.entity.OutboxEvent;
import com.wangning.seckill.mapper.OutboxEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Outbox 投递任务：定时扫 PENDING 事件投递 RocketMQ。
 *
 * <p>这是 Outbox 模式的关键一步：业务事务里写的 outbox_event 由这个任务异步投递，
 * 投递成功标记 SENT，失败重试超 maxRetry 标记 DEAD。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

    private final OutboxEventMapper outboxMapper;
    private final RocketMQTemplate rocketMQTemplate;

    /** 每 2 秒扫一次 */
    @Scheduled(fixedDelay = 2000, initialDelay = 10_000)
    public void relay() {
        List<OutboxEvent> pending = outboxMapper.selectList(
                new LambdaQueryWrapper<OutboxEvent>()
                        .eq(OutboxEvent::getStatus, "PENDING")
                        .orderByAsc(OutboxEvent::getId)
                        .last("LIMIT 50"));

        for (OutboxEvent event : pending) {
            int currentRetry = event.getRetryCount() == null ? 0 : event.getRetryCount();
            int maxRetry = event.getMaxRetry() == null ? 10 : event.getMaxRetry();
            try {
                Message msg = new Message(event.getTopic(), event.getEventType(),
                        event.getEventKey(),
                        event.getPayload().getBytes(StandardCharsets.UTF_8));
                rocketMQTemplate.getProducer().send(msg);

                // 投递成功标记 SENT
                outboxMapper.update(null,
                        new LambdaUpdateWrapper<OutboxEvent>()
                                .eq(OutboxEvent::getId, event.getId())
                                .eq(OutboxEvent::getStatus, "PENDING")
                                .eq(OutboxEvent::getRetryCount, currentRetry)
                                .set(OutboxEvent::getStatus, "SENT")
                                .set(OutboxEvent::getFailReason, null)
                                .set(OutboxEvent::getSentTime, LocalDateTime.now()));
            } catch (Exception e) {
                String reason = diagnosticReason(e);
                log.error("Outbox 投递失败 id={}, reason={}", event.getId(), reason);
                int retry = currentRetry + 1;
                String status = retry >= maxRetry ? "DEAD" : "PENDING";
                outboxMapper.update(null,
                        new LambdaUpdateWrapper<OutboxEvent>()
                                .eq(OutboxEvent::getId, event.getId())
                                .eq(OutboxEvent::getStatus, "PENDING")
                                .eq(OutboxEvent::getRetryCount, currentRetry)
                                .set(OutboxEvent::getRetryCount, retry)
                                .set(OutboxEvent::getStatus, status)
                                .set(OutboxEvent::getProcessStatus, "DEAD".equals(status) ? "FAILED" : event.getProcessStatus())
                                .set(OutboxEvent::getFailReason, reason));
            }
        }
    }

    private String diagnosticReason(Exception exception) {
        String message = exception.getMessage();
        String reason = exception.getClass().getSimpleName()
                + (message == null || message.isBlank() ? "" : ": " + message);
        return reason.length() <= 480 ? reason : reason.substring(0, 480);
    }
}
