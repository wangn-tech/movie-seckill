package com.wangning.seckill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 注册请求。
 */
public record RegisterReq(
        @NotBlank(message = "账号不能为空")
        @Pattern(regexp = "^1[3-9]\\d{9}$", message = "账号必须是手机号")
        String account,

        @NotBlank(message = "密码不能为空")
        String password,

        String nickname
) {
}
