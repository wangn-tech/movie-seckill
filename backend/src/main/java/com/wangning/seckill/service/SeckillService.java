package com.wangning.seckill.service;

import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mq.producer.OrderEventProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 抢座核心服务 — 面试重点。
 *
 * <p>链路：
 * <ol>
 *   <li>布隆过滤器校验场次存在</li>
 *   <li>Redis Lua 原子：幂等 + 座位冲突 + 库存预扣 + 锁定</li>
 *   <li>成功立即发 MQ 异步落单，返回"排队中"</li>
 *   <li>失败直接返回错误</li>
 * </ol>
 *
 * <p>三层防超卖：Redis Lua → MySQL 条件更新 → seat_lock 唯一索引兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {

    private final StringRedisTemplate redis;
    private final BloomFilterService bloom;
    private final ScheduleMapper scheduleMapper;
    private final OrderEventProducer orderEventProducer;

    @Value("${seckill.mq.topic-order:seckill-order-topic}")
    private String orderTopic;

    private final DefaultRedisScript<Long> reserveScript;

    public SeckillService(StringRedisTemplate redis,
                          BloomFilterService bloom,
                          ScheduleMapper scheduleMapper,
                          OrderEventProducer orderEventProducer) {
        this.redis = redis;
        this.bloom = bloom;
        this.scheduleMapper = scheduleMapper;
        this.orderEventProducer = orderEventProducer;
        this.reserveScript = new DefaultRedisScript<>();
        this.reserveScript.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/seat_reserve.lua")));
        this.reserveScript.setResultType(Long.class);
    }

    /**
     * 抢座入口。
     *
     * @return requestId（前端轮询订单状态用）
     */
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
        keys.add(CacheKeyConstants.stockKey(scheduleId));          // KEYS[1] stock
        keys.add(CacheKeyConstants.reservationKey(requestId));      // KEYS[2] reservation
        for (SeckillReq.Seat s : req.seats()) {
            keys.add(CacheKeyConstants.seatKey(scheduleId, s.row(), s.col())); // KEYS[3..]
        }

        int seatCount = req.seats().size();
        long seatLockTtl = 30 * 60;       // 座位锁 30 分钟
        long reservationTtl = 30 * 60 + 600; // 凭证多留 10 分钟

        // 4. 执行 Lua
        Long result = redis.execute(reserveScript, keys,
                String.valueOf(userId),
                requestId,
                String.valueOf(seatLockTtl),
                String.valueOf(reservationTtl),
                String.valueOf(seatCount));

        // 5. 根据结果处理
        if (result == 1L || result == 2L) {
            // 成功或幂等成功：发 MQ 异步落单（消息体带 userId/scheduleId/seats/requestId）
            orderEventProducer.sendOrderCreated(orderTopic, userId, scheduleId, req.seats(), requestId);
            log.info("抢座成功 userId={}, scheduleId={}, requestId={}, result={}", userId, scheduleId, requestId, result);
            return requestId;
        } else if (result == -1L) {
            throw new BizException(ResultCode.SEAT_CONFLICT);
        } else if (result == -2L) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH);
        } else {
            throw new BizException(ResultCode.SYSTEM_ERROR);
        }
    }

    /**
     * 启动时把场次库存预热到 Redis。
     */
    public void preloadStock(Long scheduleId, int stock) {
        redis.opsForValue().set(CacheKeyConstants.stockKey(scheduleId), String.valueOf(stock));
    }
}
