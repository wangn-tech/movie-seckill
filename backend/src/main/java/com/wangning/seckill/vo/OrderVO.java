package com.wangning.seckill.vo;

import com.wangning.seckill.entity.TicketOrder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record OrderVO(
        Long id,
        String orderNo,
        String requestId,
        String movieName,
        String cinemaName,
        String showTime,
        Integer seatCount,
        String seatsInfo,
        BigDecimal totalPrice,
        Integer status,
        LocalDateTime expireTime,
        LocalDateTime payTime,
        LocalDateTime createTime) {

    public static OrderVO from(TicketOrder order) {
        if (order == null) {
            return null;
        }
        return new OrderVO(order.getId(), order.getOrderNo(), order.getLockToken(),
                order.getMovieName(), order.getCinemaName(), order.getShowTime(),
                order.getSeatCount(), order.getSeatsInfo(), order.getTotalPrice(), order.getStatus(),
                order.getExpireTime(), order.getPayTime(), order.getCreateTime());
    }
}
