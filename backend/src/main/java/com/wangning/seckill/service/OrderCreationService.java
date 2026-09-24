package com.wangning.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.wangning.seckill.dto.OrderCreateEvent;
import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.entity.*;
import com.wangning.seckill.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderCreationService {

    private final TicketOrderMapper orderMapper;
    private final OrderSeatMapper orderSeatMapper;
    private final SeatLockMapper seatLockMapper;
    private final ScheduleMapper scheduleMapper;
    private final OutboxEventMapper outboxMapper;
    private final MovieMapper movieMapper;
    private final CinemaMapper cinemaMapper;

    @Transactional(rollbackFor = Exception.class)
    public void create(OrderCreateEvent event) {
        TicketOrder existing = orderMapper.selectOne(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getLockToken, event.requestId()));
        if (existing != null) {
            if (!sameRequest(existing, event)) {
                throw new IllegalStateException("requestId 已用于其他订单");
            }
            markSucceeded(event.requestId());
            return;
        }

        Schedule schedule = scheduleMapper.selectById(event.scheduleId());
        if (schedule == null || !Integer.valueOf(1).equals(schedule.getStatus())) {
            throw new IllegalStateException("场次不存在或已停止售票");
        }

        int seatCount = event.seats().size();
        int updated = scheduleMapper.deductStock(event.scheduleId(), seatCount);
        if (updated == 0) {
            throw new IllegalStateException("MySQL库存不足");
        }

        Movie movie = movieMapper.selectById(schedule.getMovieId());
        Cinema cinema = cinemaMapper.selectById(schedule.getCinemaId());
        String orderNo = "SO" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);

        TicketOrder order = new TicketOrder();
        order.setOrderNo(orderNo);
        order.setUserId(event.userId());
        order.setScheduleId(event.scheduleId());
        order.setLockToken(event.requestId());
        order.setMovieName(movie == null ? "" : movie.getName());
        order.setCinemaName(cinema == null ? "" : cinema.getName());
        order.setShowTime(schedule.getShowDate() + " " + schedule.getShowTime());
        order.setSeatCount(seatCount);
        order.setTotalPrice(schedule.getPrice().multiply(BigDecimal.valueOf(seatCount)));
        order.setStatus(0);
        order.setExpireTime(LocalDateTime.now().plusMinutes(30));
        order.setSeatsInfo(event.seats().stream()
                .map(seat -> seat.row() + "排" + seat.col() + "座")
                .reduce((left, right) -> left + "," + right)
                .orElse(""));
        orderMapper.insert(order);

        for (SeckillReq.Seat seat : event.seats()) {
            // 已取消订单留下的锁记录先删除；有效锁仍由唯一键拒绝重售。
            seatLockMapper.delete(new LambdaQueryWrapper<SeatLock>()
                    .eq(SeatLock::getScheduleId, event.scheduleId())
                    .eq(SeatLock::getRowNum, seat.row())
                    .eq(SeatLock::getColNum, seat.col())
                    .eq(SeatLock::getStatus, 0));

            OrderSeat orderSeat = new OrderSeat();
            orderSeat.setOrderId(order.getId());
            orderSeat.setOrderNo(orderNo);
            orderSeat.setScheduleId(event.scheduleId());
            orderSeat.setRowNum(seat.row());
            orderSeat.setColNum(seat.col());
            orderSeatMapper.insert(orderSeat);

            SeatLock lock = new SeatLock();
            lock.setScheduleId(event.scheduleId());
            lock.setRowNum(seat.row());
            lock.setColNum(seat.col());
            lock.setUserId(event.userId());
            lock.setLockToken(event.requestId());
            lock.setOrderNo(orderNo);
            lock.setLockUntil(order.getExpireTime());
            lock.setStatus(1);
            seatLockMapper.insert(lock);
        }

        markSucceeded(event.requestId());
        log.info("订单创建成功 orderNo={}, requestId={}, seatCount={}", orderNo, event.requestId(), seatCount);
    }

    private void markSucceeded(String requestId) {
        outboxMapper.update(null, new LambdaUpdateWrapper<OutboxEvent>()
                .eq(OutboxEvent::getEventKey, requestId)
                .set(OutboxEvent::getProcessStatus, "SUCCEEDED")
                .set(OutboxEvent::getFailReason, null));
    }

    private boolean sameRequest(TicketOrder existing, OrderCreateEvent event) {
        if (!event.userId().equals(existing.getUserId())
                || !event.scheduleId().equals(existing.getScheduleId())
                || event.seats().size() != existing.getSeatCount()) {
            return false;
        }
        String seatsInfo = event.seats().stream()
                .sorted(Comparator.comparingInt(SeckillReq.Seat::row)
                        .thenComparingInt(SeckillReq.Seat::col))
                .map(seat -> seat.row() + "排" + seat.col() + "座")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        return seatsInfo.equals(existing.getSeatsInfo());
    }
}
