package com.wangning.seckill.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：签发 AccessToken / RefreshToken，校验并解析。
 *
 * <p>AccessToken 短期（30min），每次请求都带；RefreshToken 长期（7d），存在 Redis 支持登出失效。
 */
@Slf4j
@Component
public class JwtUtil {

    @Value("${seckill.jwt.secret}")
    private String secret;

    @Value("${seckill.jwt.access-token-ttl-minutes:30}")
    private long accessTtlMinutes;

    @Value("${seckill.jwt.refresh-token-ttl-days:7}")
    private long refreshTtlDays;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    /** 签发 AccessToken */
    public String createAccessToken(Long userId) {
        return buildToken(userId, "access", accessTtlMinutes * 60_000);
    }

    /** 签发 RefreshToken */
    public String createRefreshToken(Long userId) {
        return buildToken(userId, "refresh", refreshTtlDays * 86_400_000);
    }

    private String buildToken(Long userId, String type, long ttlMs) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("type", type)
                .issuedAt(new Date(now))
                .expiration(new Date(now + ttlMs))
                .signWith(key)
                .compact();
    }

    /**
     * 解析 token，失败抛异常。
     * @return userId
     */
    public Long parseUserId(String token) {
        Claims c = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return Long.valueOf(c.getSubject());
    }

    /**
     * 校验是否为 refresh token。
     */
    public boolean isRefreshToken(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return "refresh".equals(c.get("type"));
        } catch (Exception e) {
            return false;
        }
    }
}
