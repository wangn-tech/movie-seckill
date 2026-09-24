package com.wangning.seckill.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.entity.TicketOrder;
import com.wangning.seckill.mapper.TicketOrderMapper;
import com.wangning.seckill.service.OrderLifecycleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/** 每分钟扫描超时订单；每个订单的 DB 变更与补偿 Outbox 在独立事务中完成。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutJob {

    private final TicketOrderMapper orderMapper;
    private final OrderLifecycleService lifecycleService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void closeTimeoutOrders() {
        List<TicketOrder> orders = orderMapper.selectList(new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, 0)
                .lt(TicketOrder::getExpireTime, LocalDateTime.now())
                .last("LIMIT 20"));
        for (TicketOrder order : orders) {
            try {
                lifecycleService.cancelExpired(order.getId());
            } catch (Exception e) {
                log.error("超时关单失败 orderNo={}", order.getOrderNo(), e);
            }
        }
    }
}
