package com.wangning.seckill.common.exception;

import lombok.Getter;

/**
 * 业务异常：可预期的错误，全局异常处理器会转成 Result 返回。
 */
@Getter
public class BizException extends RuntimeException {

    private final int code;

    public BizException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BizException(ResultCode resultCode, String overrideMsg) {
        super(overrideMsg);
        this.code = resultCode.getCode();
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }
}
