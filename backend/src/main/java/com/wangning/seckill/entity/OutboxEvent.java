package com.wangning.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("outbox_event")
public class OutboxEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String eventType;
    private String topic;
    private String payload;

    /** PENDING / SENT / DEAD */
    private String status;

    private Integer retryCount;
    private Integer maxRetry;

    private LocalDateTime createTime;
    private LocalDateTime sentTime;
}
