package com.wangning.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 场次 PO — 防超卖核心表。
 */
@Data
@TableName("movie_schedule")
public class Schedule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long movieId;
    private Long cinemaId;
    private String hallName;
    private LocalDate showDate;
    private String showTime;

    private Integer totalSeats;
    private Integer availableSeats;
    private BigDecimal price;

    /** 1可售 0停售 */
    private Integer status;

    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
