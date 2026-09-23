package com.wangning.seckill.common.exception;

import lombok.Getter;

/**
 * 业务异常码枚举。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "成功"),
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "无权限"),
    NOT_FOUND(404, "资源不存在"),
    TOO_MANY_REQUESTS(429, "请求过于频繁，请稍后再试"),

    // 用户相关 10xx
    USER_NOT_EXIST(1001, "用户不存在"),
    PASSWORD_WRONG(1002, "账号或密码错误"),
    ACCOUNT_EXISTS(1003, "账号已注册"),
    TOKEN_INVALID(1004, "Token 无效"),
    REFRESH_TOKEN_EXPIRED(1005, "RefreshToken 已过期，请重新登录"),

    // 秒杀相关 20xx
    STOCK_NOT_ENOUGH(2001, "已抢光"),
    SEAT_CONFLICT(2002, "座位已被占用"),
    REPEAT_ORDER(2003, "您已经抢过该场次的票了"),
    ORDER_NOT_FOUND(2004, "订单不存在"),
    ORDER_STATUS_ILLEGAL(2005, "订单状态不允许此操作"),
    BALANCE_NOT_ENOUGH(2006, "余额不足"),
    SCHEDULE_NOT_STARTED(2007, "秒杀尚未开始"),
    SCHEDULE_ENDED(2008, "秒杀已结束"),

    // 系统相关 50xx
    SYSTEM_ERROR(5000, "系统繁忙，请稍后再试"),
    REDIS_ERROR(5001, "缓存服务异常");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
