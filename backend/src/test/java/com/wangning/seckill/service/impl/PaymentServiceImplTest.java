package com.wangning.seckill.service.impl;

import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.OrderSeatMapper;
import com.wangning.seckill.mapper.OutboxEventMapper;
import com.wangning.seckill.mapper.SeatLockMapper;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.mapper.UserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PaymentServiceImplTest {

    @Test
    void rejectsPaymentAfterOrderExpiry() {
        TicketOrderMapper orderMapper = mock(TicketOrderMapper.class);
        UserMapper userMapper = mock(UserMapper.class);
        SeatLockMapper seatLockMapper = mock(SeatLockMapper.class);
        OrderSeatMapper orderSeatMapper = mock(OrderSeatMapper.class);
        OutboxEventMapper outboxMapper = mock(OutboxEventMapper.class);
        PaymentServiceImpl service = new PaymentServiceImpl(orderMapper, userMapper, seatLockMapper,
                orderSeatMapper, outboxMapper, new ObjectMapper());

        TicketOrder order = new TicketOrder();
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().minusSeconds(1));
        when(orderMapper.selectOne(any())).thenReturn(order);

        assertThatThrownBy(() -> service.pay(7L, "SO-expired"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("超时");
        verifyNoInteractions(userMapper, seatLockMapper, orderSeatMapper, outboxMapper);
    }
}
