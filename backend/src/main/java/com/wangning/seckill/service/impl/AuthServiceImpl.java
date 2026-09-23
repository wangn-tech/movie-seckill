package com.wangning.seckill.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.common.exception.BizException;
import com.wangning.seckill.common.exception.ResultCode;
import com.wangning.seckill.common.util.JwtUtil;
import com.wangning.seckill.dto.LoginReq;
import com.wangning.seckill.dto.RegisterReq;
import com.wangning.seckill.entity.User;
import com.wangning.seckill.mapper.UserMapper;
import com.wangning.seckill.service.AuthService;
import com.wangning.seckill.vo.LoginVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 认证服务实现：注册/登录/刷新 Token/登出。
 *
 * <p>RefreshToken 存 Redis，登出时直接删除，支持服务端强制下线。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate redis;

    @Override
    public Long register(RegisterReq req) {
        // 检查账号是否已存在
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, req.account()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.ACCOUNT_EXISTS);
        }
        User u = new User();
        u.setAccount(req.account());
        u.setPassword(BCrypt.hashpw(req.password()));
        u.setNickname(req.nickname() != null ? req.nickname() : "用户" + req.account().substring(7));
        u.setBalance(100000); // 默认 1000 元
        userMapper.insert(u);
        return u.getId();
    }

    @Override
    public LoginVO login(LoginReq req) {
        User u = userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getAccount, req.account()));
        if (u == null || !BCrypt.checkpw(req.password(), u.getPassword())) {
            throw new BizException(ResultCode.PASSWORD_WRONG);
        }
        return issueTokens(u);
    }

    @Override
    public LoginVO refresh(String refreshToken) {
        // 校验签名和类型
        if (!jwtUtil.isRefreshToken(refreshToken)) {
            throw new BizException(ResultCode.TOKEN_INVALID);
        }
        Long userId = jwtUtil.parseUserId(refreshToken);
        // Redis 中必须存在该 refresh token（支持登出失效）
        String cached = redis.opsForValue().get(CacheKeyConstants.refreshToken(userId));
        if (cached == null || !cached.equals(refreshToken)) {
            throw new BizException(ResultCode.REFRESH_TOKEN_EXPIRED);
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            throw new BizException(ResultCode.USER_NOT_EXIST);
        }
        return issueTokens(u);
    }

    @Override
    public void logout(Long userId) {
        redis.delete(CacheKeyConstants.refreshToken(userId));
    }

    /**
     * 签发双 Token 并把 RefreshToken 写入 Redis。
     */
    private LoginVO issueTokens(User u) {
        String access = jwtUtil.createAccessToken(u.getId());
        String refresh = jwtUtil.createRefreshToken(u.getId());
        // RefreshToken 在 Redis 存 7 天，登出即删
        redis.opsForValue().set(CacheKeyConstants.refreshToken(u.getId()), refresh, Duration.ofDays(7));
        return LoginVO.builder()
                .userId(u.getId())
                .nickname(u.getNickname())
                .accessToken(access)
                .refreshToken(refresh)
                .build();
    }
}
