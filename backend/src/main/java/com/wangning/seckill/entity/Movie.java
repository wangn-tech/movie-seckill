package com.wangning.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 电影 PO。
 */
@Data
@TableName("movie")
public class Movie {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;
    private String poster;
    private BigDecimal score;
    private String actors;
    private String genre;
    private Integer duration;
    private String description;

    /** 1热映 2即将上映 */
    private Integer status;

    private Integer version;
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
