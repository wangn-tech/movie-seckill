package com.wangning.seckill.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.entity.OutboxEvent;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.service.RedisSeatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SeckillServiceImplTest {

    private BloomFilterService bloom;
    private ScheduleMapper scheduleMapper;
    private TicketOrderMapper orderMapper;
    private OutboxEventMapper outboxMapper;
    private RedisSeatService redisSeatService;
    private SeckillServiceImpl service;

    @BeforeEach
    void setUp() {
        bloom = mock(BloomFilterService.class);
        scheduleMapper = mock(ScheduleMapper.class);
        orderMapper = mock(TicketOrderMapper.class);
        outboxMapper = mock(OutboxEventMapper.class);
        redisSeatService = mock(RedisSeatService.class);
        service = new SeckillServiceImpl(mock(StringRedisTemplate.class), bloom, scheduleMapper,
                orderMapper, outboxMapper, redisSeatService, new ObjectMapper());
        ReflectionTestUtils.setField(service, "orderTopic", "seckill-order-topic");

        when(bloom.mightContainSchedule(1L)).thenReturn(true);
        Schedule schedule = new Schedule();
        schedule.setId(1L);
        schedule.setStatus(1);
        schedule.setTotalSeats(120);
        schedule.setSeatRows(10);
        schedule.setSeatCols(14);
        schedule.setUnavailableSeats("[{\"row\":1,\"col\":5}]");
        when(scheduleMapper.selectById(1L)).thenReturn(schedule);
    }

    @Test
    void acceptsMoreThanFourValidSeats() {
        List<SeckillReq.Seat> seats = List.of(
                new SeckillReq.Seat(1, 1), new SeckillReq.Seat(1, 2),
                new SeckillReq.Seat(1, 3), new SeckillReq.Seat(1, 4),
                new SeckillReq.Seat(1, 6));
        when(redisSeatService.reserve(eq(7L), eq(1L), eq("request_123"), anyString(),
                eq(seats), anyLong(), anyLong())).thenReturn(1L);
        when(outboxMapper.selectCount(any())).thenReturn(0L);
        OutboxEvent processing = new OutboxEvent();
        processing.setEventKey("request_123");
        processing.setProcessStatus("PROCESSING");
        when(outboxMapper.selectOne(any())).thenReturn(processing);

        var result = service.seize(7L, new SeckillReq(1L, seats, "request_123"));

        assertThat(result.status()).isEqualTo("PROCESSING");
        verify(outboxMapper).insert(argThat((OutboxEvent event) -> event.getEventKey().equals("request_123")
                && event.getPayload().contains("\"row\":1")));
    }

    @Test
    void rejectsDuplicateAndUnavailableSeatsBeforeRedis() {
        assertThatThrownBy(() -> service.seize(7L, new SeckillReq(1L,
                List.of(new SeckillReq.Seat(1, 1), new SeckillReq.Seat(1, 1)), "request_123")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无效");

        assertThatThrownBy(() -> service.seize(7L, new SeckillReq(1L,
                List.of(new SeckillReq.Seat(1, 5)), "request_456")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("无效");

        verifyNoInteractions(redisSeatService);
    }

    @Test
    void rejectsRequestIdReusedWithDifferentPayload() {
        when(redisSeatService.reserve(anyLong(), anyLong(), anyString(), anyString(), anyList(),
                anyLong(), anyLong())).thenReturn(-3L);

        assertThatThrownBy(() -> service.seize(7L, new SeckillReq(1L,
                List.of(new SeckillReq.Seat(2, 2)), "request_789")))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("requestId");

        verify(outboxMapper, never()).insert(any(OutboxEvent.class));
    }
}
