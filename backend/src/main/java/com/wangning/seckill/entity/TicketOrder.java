package com.wangning.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ticket_order")
public class TicketOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;
    private Long userId;
    private Long scheduleId;

    /** 幂等键 = requestId */
    private String lockToken;

    private String movieName;
    private String cinemaName;
    private String showTime;
    private Integer seatCount;
    private String seatsInfo;
    private java.math.BigDecimal totalPrice;

    /** 0待支付 1已支付 2已取消 */
    private Integer status;

    private LocalDateTime expireTime;
    private LocalDateTime payTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
