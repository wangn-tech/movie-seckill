package com.wangning.seckill.service;

import com.wangning.seckill.dto.LoginReq;
import com.wangning.seckill.dto.RegisterReq;
import com.wangning.seckill.vo.LoginVO;

public interface AuthService {

    /** 注册 */
    Long register(RegisterReq req);

    /** 登录，返回双 Token */
    LoginVO login(LoginReq req);

    /** 用 RefreshToken 换新的 AccessToken */
    LoginVO refresh(String refreshToken);

    /** 登出：删除 Redis 中的 RefreshToken */
    void logout(Long userId);
}
