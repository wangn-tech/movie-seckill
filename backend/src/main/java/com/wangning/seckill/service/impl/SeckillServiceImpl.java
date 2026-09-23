package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.entity.OutboxEvent;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.service.SeckillService;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 抢座核心服务实现 — 面试重点。
 *
 * <p>链路：
 * <ol>
 *   <li>布隆过滤器校验场次存在</li>
 *   <li>Redis Lua 原子：幂等 + 座位冲突 + 库存预扣 + 锁定</li>
 *   <li>写 outbox_event(PENDING)，由 OutboxRelayJob 投递 MQ（最终一致）</li>
 * </ol>
 */
@Slf4j
@Service
public class SeckillServiceImpl implements SeckillService {

    private final StringRedisTemplate redis;
    private final BloomFilterService bloom;
    private final ScheduleMapper scheduleMapper;
    private final OutboxEventMapper outboxMapper;
    private final DefaultMQProducer mqProducer;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${seckill.mq.topic-order:seckill-order-topic}")
    private String orderTopic;

    private final DefaultRedisScript<Long> reserveScript;

    public SeckillServiceImpl(StringRedisTemplate redis,
                             BloomFilterService bloom,
                             ScheduleMapper scheduleMapper,
                             OutboxEventMapper outboxMapper,
                             DefaultMQProducer mqProducer) {
        this.redis = redis;
        this.bloom = bloom;
        this.scheduleMapper = scheduleMapper;
        this.outboxMapper = outboxMapper;
        this.mqProducer = mqProducer;
        this.reserveScript = new DefaultRedisScript<>();
        this.reserveScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seat_reserve.lua")));
        this.reserveScript.setResultType(Long.class);
    }

    @Override
    public String seize(Long userId, SeckillReq req) {
        Long scheduleId = req.scheduleId();

        // 1. 布隆过滤器：不存在的场次直接拒绝，防穿透
        if (!bloom.mightContainSchedule(scheduleId)) {
            throw new BizException(ResultCode.NOT_FOUND, "场次不存在");
        }

        // 2. requestId 幂等：前端没传就生成
        String requestId = req.requestId() != null ? req.requestId() : UUID.randomUUID().toString();

        // 3. 组装 Lua KEYS
        List<String> keys = new ArrayList<>();
        keys.add(CacheKeyConstants.stockKey(scheduleId));
        keys.add(CacheKeyConstants.reservationKey(requestId));
        for (SeckillReq.Seat s : req.seats()) {
            keys.add(CacheKeyConstants.seatKey(scheduleId, s.row(), s.col()));
        }

        int seatCount = req.seats().size();
        long seatLockTtl = 30 * 60;
        long reservationTtl = 30 * 60 + 600;

        // 4. 执行 Lua 原子扣库存
        Long result = redis.execute(reserveScript, keys,
                String.valueOf(userId), requestId,
                String.valueOf(seatLockTtl),
                String.valueOf(reservationTtl),
                String.valueOf(seatCount));

        // 5. 根据 Lua 结果处理
        if (result == 1L || result == 2L) {
            // 幂等：如果 outbox 已有记录，说明已处理过
            Long exists = outboxMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                    .eq(OutboxEvent::getTopic, orderTopic)
                    .last("LIMIT 1"));
            // 写 outbox_event，由 OutboxRelayJob 异步投递 MQ（最终一致兜底）
            String payload = buildPayload(userId, scheduleId, requestId, req.seats());
            OutboxEvent event = new OutboxEvent();
            event.setEventType("ORDER_CREATED");
            event.setTopic(orderTopic);
            event.setPayload(payload);
            event.setStatus("PENDING");
            event.setRetryCount(0);
            event.setMaxRetry(10);
            outboxMapper.insert(event);

            // 立即尝试投递一次（不等 2 秒轮询），成功标记 SENT
            try {
                Message msg = new Message(orderTopic, "ORDER_CREATED",
                        requestId, payload.getBytes(StandardCharsets.UTF_8));
                mqProducer.send(msg);
                event.setStatus("SENT");
                event.setSentTime(LocalDateTime.now());
                outboxMapper.updateById(event);
                log.info("抢座成功并直接投递 MQ requestId={}", requestId);
            } catch (Exception mqEx) {
                log.warn("MQ 直接投递失败，由 OutboxRelayJob 兜底 requestId={}: {}", requestId, mqEx.getMessage());
            }

            log.info("抢座成功 userId={}, scheduleId={}, requestId={}", userId, scheduleId, requestId);
            return requestId;
        } else if (result == -1L) {
            throw new BizException(ResultCode.SEAT_CONFLICT);
        } else if (result == -2L) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH);
        } else {
            throw new BizException(ResultCode.SYSTEM_ERROR);
        }
    }

    private String buildPayload(Long userId, Long scheduleId, String requestId, List<SeckillReq.Seat> seats) {
        try {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("userId", userId);
            m.put("scheduleId", scheduleId);
            m.put("requestId", requestId);
            m.put("seats", seats);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            throw new IllegalStateException("序列化抢座消息失败", e);
        }
    }

    @Override
    public void preloadStock(Long scheduleId, int stock) {
        redis.opsForValue().set(CacheKeyConstants.stockKey(scheduleId), String.valueOf(stock));
    }
}
