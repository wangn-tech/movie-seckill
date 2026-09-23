package com.wangning.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("cinema")
public class Cinema {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String address;
    private String city;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
