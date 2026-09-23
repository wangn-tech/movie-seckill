package com.wangning.seckill.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户表 PO。
 */
@Data
@TableName("sys_user")
public class User {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 账号/手机号 */
    private String account;

    /** BCrypt 加密后的密码 */
    private String password;

    private String nickname;

    /** 模拟余额，单位分 */
    private Integer balance;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
