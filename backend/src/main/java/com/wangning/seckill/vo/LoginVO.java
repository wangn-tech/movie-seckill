package com.wangning.seckill.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 登录返回：双 Token + 用户基本信息。
 */
@Data
@Builder
public class LoginVO {
    private Long userId;
    private String nickname;
    private String accessToken;
    private String refreshToken;
}
