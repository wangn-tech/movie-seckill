package com.wangning.seckill.service.impl;

import cn.hutool.crypto.SecureUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.dto.OrderCreateEvent;
import com.wangning.seckill.entity.OutboxEvent;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.service.RedisSeatService;
import com.wangning.seckill.service.SeckillService;
import com.wangning.seckill.vo.OrderVO;
import com.wangning.seckill.vo.SeatLayoutVO;
import com.wangning.seckill.vo.SeckillStatusVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements SeckillService {

    private static final long SEAT_LOCK_TTL = 30 * 60;
    private static final long RESERVATION_TTL = SEAT_LOCK_TTL + 10 * 60;

    private final StringRedisTemplate redis;
    private final BloomFilterService bloom;
    private final ScheduleMapper scheduleMapper;
    private final TicketOrderMapper orderMapper;
    private final OutboxEventMapper outboxMapper;
    private final RedisSeatService redisSeatService;
    private final ObjectMapper objectMapper;

    @Value("${seckill.mq.topic-order:seckill-order-topic}")
    private String orderTopic;

    @Override
    public SeckillStatusVO seize(Long userId, SeckillReq req) {
        // 热路径只做轻量校验和 Redis 原子预扣，不等待数据库落单或 MQ 响应。
        Long scheduleId = req.scheduleId();
        if (!bloom.mightContainSchedule(scheduleId)) {
            throw new BizException(ResultCode.NOT_FOUND, "场次不存在");
        }

        Schedule schedule = scheduleMapper.selectById(scheduleId);
        if (schedule == null) {
            throw new BizException(ResultCode.NOT_FOUND, "场次不存在");
        }
        if (!Integer.valueOf(1).equals(schedule.getStatus())) {
            throw new BizException(ResultCode.SCHEDULE_ENDED);
        }

        List<SeckillReq.Seat> seats = req.seats().stream()
                .sorted(Comparator.comparingInt(SeckillReq.Seat::row).thenComparingInt(SeckillReq.Seat::col))
                .toList();
        validateSeats(schedule, seats);

        String fingerprint = fingerprint(userId, scheduleId, seats);
        // Lua 同时完成 requestId 幂等、座位冲突、库存预扣和座位 TTL 锁定。
        long result = redisSeatService.reserve(userId, scheduleId, req.requestId(), fingerprint,
                seats, SEAT_LOCK_TTL, RESERVATION_TTL);

        if (result == -1) {
            throw new BizException(ResultCode.SEAT_CONFLICT);
        }
        if (result == -2) {
            throw new BizException(ResultCode.STOCK_NOT_ENOUGH);
        }
        if (result == -3) {
            throw new BizException(ResultCode.IDEMPOTENCY_CONFLICT);
        }
        if (result != 1 && result != 2) {
            throw new BizException(ResultCode.REDIS_ERROR);
        }

        // 只写本地 Outbox，Relay 负责至少一次投递；前端随后轮询 PROCESSING 状态。
        ensureOutbox(userId, scheduleId, req.requestId(), seats, result == 1);
        return SeckillStatusVO.processing(req.requestId());
    }

    @Override
    public SeckillStatusVO status(Long userId, String requestId) {
        TicketOrder order = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getLockToken, requestId)
                .eq(TicketOrder::getUserId, userId));
        if (order != null) {
            return new SeckillStatusVO(requestId, "CREATED", "订单创建成功", OrderVO.from(order));
        }

        OutboxEvent event = outboxMapper.selectOne(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventKey, requestId)
                .eq(OutboxEvent::getUserId, userId));
        if (event == null) {
            throw new BizException(ResultCode.NOT_FOUND, "抢座请求不存在");
        }
        if ("FAILED".equals(event.getProcessStatus())) {
            return new SeckillStatusVO(requestId, "FAILED",
                    event.getFailReason() == null ? ResultCode.ORDER_PROCESSING_FAILED.getMessage() : event.getFailReason(),
                    null);
        }
        if ("DEAD".equals(event.getStatus())) {
            return new SeckillStatusVO(requestId, "FAILED",
                    event.getFailReason() == null ? ResultCode.ORDER_PROCESSING_FAILED.getMessage() : event.getFailReason(),
                    null);
        }
        return SeckillStatusVO.processing(requestId);
    }

    private void ensureOutbox(Long userId, Long scheduleId, String requestId,
                              List<SeckillReq.Seat> seats, boolean newlyReserved) {
        if (outboxMapper.selectCount(new LambdaQueryWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventKey, requestId)) > 0) {
            return;
        }

        try {
            OutboxEvent event = new OutboxEvent();
            event.setEventKey(requestId);
            event.setUserId(userId);
            event.setScheduleId(scheduleId);
            event.setEventType("ORDER_CREATE");
            event.setTopic(orderTopic);
            event.setPayload(objectMapper.writeValueAsString(
                    new OrderCreateEvent(userId, scheduleId, requestId, seats)));
            event.setStatus("PENDING");
            event.setProcessStatus("PROCESSING");
            event.setRetryCount(0);
            event.setMaxRetry(10);
            outboxMapper.insert(event);
        } catch (DuplicateKeyException ignored) {
            // 并发重试由 uk_event_key 收敛为一个入口事件。
        } catch (Exception e) {
            if (newlyReserved) {
                redisSeatService.release(userId, scheduleId, requestId, seats, seats.size());
            }
            throw new BizException(ResultCode.SYSTEM_ERROR, "请求入队失败，库存已释放");
        }
    }

    private void validateSeats(Schedule schedule, List<SeckillReq.Seat> seats) {
        if (seats.isEmpty() || seats.size() > schedule.getTotalSeats()) {
            throw new BizException(ResultCode.INVALID_SEAT);
        }

        Set<String> selected = new HashSet<>();
        Set<String> unavailable = parseUnavailable(schedule.getUnavailableSeats());
        for (SeckillReq.Seat seat : seats) {
            String coordinate = seat.row() + "-" + seat.col();
            boolean outside = seat.row() < 1 || seat.row() > schedule.getSeatRows()
                    || seat.col() < 1 || seat.col() > schedule.getSeatCols();
            if (outside || unavailable.contains(coordinate) || !selected.add(coordinate)) {
                throw new BizException(ResultCode.INVALID_SEAT);
            }
        }
    }

    private Set<String> parseUnavailable(String json) {
        if (json == null || json.isBlank()) {
            return Set.of();
        }
        try {
            List<SeatLayoutVO.Seat> seats = objectMapper.readValue(json, new TypeReference<>() { });
            Set<String> result = new HashSet<>();
            seats.forEach(seat -> result.add(seat.row() + "-" + seat.col()));
            return result;
        } catch (Exception e) {
            throw new IllegalStateException("场次座位布局配置错误", e);
        }
    }

    private String fingerprint(Long userId, Long scheduleId, List<SeckillReq.Seat> seats) {
        StringBuilder canonical = new StringBuilder()
                .append(userId).append(':').append(scheduleId).append(':');
        seats.forEach(seat -> canonical.append(seat.row()).append('-').append(seat.col()).append(','));
        return SecureUtil.sha256(canonical.toString());
    }

    @Override
    public void preloadStock(Long scheduleId, int stock) {
        redis.opsForValue().set(com.wangning.seckill.common.constant.CacheKeyConstants.stockKey(scheduleId),
                String.valueOf(stock));
    }

}
