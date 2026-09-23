package com.wangning.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("seat_lock")
public class SeatLock {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long scheduleId;
    private Integer rowNum;
    private Integer colNum;
    private Long userId;

    /** = requestId */
    private String lockToken;

    private String orderNo;
    private LocalDateTime lockUntil;

    /** 1锁定中 0已释放 2已售 */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
