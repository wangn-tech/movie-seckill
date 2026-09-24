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
    /** 座位图边界；实际可售数不等于行列乘积。 */
    private Integer seatRows;
    private Integer seatCols;
    /** JSON 坐标数组，表示过道、空位、维修位等不可售位置。 */
    private String unavailableSeats;
    private BigDecimal price;

    /** 1可售 0停售 */
    private Integer status;

    @com.baomidou.mybatisplus.annotation.Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
