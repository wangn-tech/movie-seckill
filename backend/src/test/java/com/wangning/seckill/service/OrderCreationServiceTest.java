package com.wangning.seckill.service;

import com.wangning.seckill.dto.OrderCreateEvent;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.entity.Cinema;
import com.wangning.seckill.entity.Movie;
import com.wangning.seckill.entity.Schedule;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.CinemaMapper;
import com.wangning.seckill.mapper.MovieMapper;
import com.wangning.seckill.mapper.OrderSeatMapper;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.ScheduleMapper;
import com.wangning.seckill.mapper.SeatLockMapper;
import com.wangning.seckill.mapper.TicketOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderCreationServiceTest {

    private TicketOrderMapper orderMapper;
    private ScheduleMapper scheduleMapper;
    private OrderCreationService service;

    @BeforeEach
    void setUp() {
        orderMapper = mock(TicketOrderMapper.class);
        OrderSeatMapper orderSeatMapper = mock(OrderSeatMapper.class);
        SeatLockMapper seatLockMapper = mock(SeatLockMapper.class);
        scheduleMapper = mock(ScheduleMapper.class);
        OutboxEventMapper outboxMapper = mock(OutboxEventMapper.class);
        MovieMapper movieMapper = mock(MovieMapper.class);
        CinemaMapper cinemaMapper = mock(CinemaMapper.class);
        service = new OrderCreationService(orderMapper, orderSeatMapper, seatLockMapper,
                scheduleMapper, outboxMapper, movieMapper, cinemaMapper);
    }

    @Test
    void insufficientDatabaseStockStopsOrderCreation() {
        when(orderMapper.selectOne(any())).thenReturn(null);
        when(scheduleMapper.selectById(9L)).thenReturn(schedule());
        when(scheduleMapper.deductStock(eq(9L), eq(2))).thenReturn(0);

        assertThatThrownBy(() -> service.create(event("request-456")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("库存不足");

        verify(orderMapper, never()).insert(any(TicketOrder.class));
    }

    private OrderCreateEvent event(String requestId) {
        return new OrderCreateEvent(7L, 9L, requestId,
                List.of(new SeckillReq.Seat(1, 1), new SeckillReq.Seat(1, 2)));
    }

    private Schedule schedule() {
        Schedule schedule = new Schedule();
        schedule.setId(9L);
        schedule.setMovieId(3L);
        schedule.setCinemaId(4L);
        schedule.setStatus(1);
        schedule.setPrice(new BigDecimal("39.90"));
        return schedule;
    }
}
