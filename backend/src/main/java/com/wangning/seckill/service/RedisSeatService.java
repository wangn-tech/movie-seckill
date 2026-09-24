package com.wangning.seckill.service;

import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.dto.SeckillReq;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class RedisSeatService {

    private final StringRedisTemplate redis;
    private final DefaultRedisScript<Long> reserveScript;
    private final DefaultRedisScript<Long> releaseScript;
    private final DefaultRedisScript<Long> confirmScript;

    public RedisSeatService(StringRedisTemplate redis) {
        this.redis = redis;
        this.reserveScript = script("lua/seat_reserve.lua");
        this.releaseScript = script("lua/seat_release.lua");
        this.confirmScript = script("lua/seat_confirm.lua");
    }

    public long reserve(Long userId, Long scheduleId, String requestId, String fingerprint,
                        List<SeckillReq.Seat> seats, long lockTtl, long reservationTtl) {
        List<String> keys = seatKeys(scheduleId, requestId, seats, false);
        Long result = redis.execute(reserveScript, keys,
                String.valueOf(userId), requestId, fingerprint,
                String.valueOf(lockTtl), String.valueOf(reservationTtl), String.valueOf(seats.size()));
        return result == null ? 0 : result;
    }

    public long release(Long userId, Long scheduleId, String requestId,
                        List<SeckillReq.Seat> seats, int seatCount) {
        List<String> keys = seatKeys(scheduleId, requestId, seats, true);
        Long result = redis.execute(releaseScript, keys,
                String.valueOf(userId), requestId, String.valueOf(seatCount));
        return result == null ? 0 : result;
    }

    public void confirm(Long userId, Long scheduleId, String requestId, String orderNo,
                        List<SeckillReq.Seat> seats) {
        List<String> keys = new ArrayList<>();
        keys.add(CacheKeyConstants.reservationKey(requestId));
        seats.forEach(seat -> keys.add(CacheKeyConstants.seatKey(scheduleId, seat.row(), seat.col())));
        redis.execute(confirmScript, keys, String.valueOf(userId), requestId, orderNo);
    }

    private List<String> seatKeys(Long scheduleId, String requestId,
                                  List<SeckillReq.Seat> seats, boolean release) {
        List<String> keys = new ArrayList<>();
        keys.add(CacheKeyConstants.stockKey(scheduleId));
        keys.add(CacheKeyConstants.reservationKey(requestId));
        if (release) {
            keys.add(CacheKeyConstants.releaseMarker(requestId));
        }
        seats.forEach(seat -> keys.add(CacheKeyConstants.seatKey(scheduleId, seat.row(), seat.col())));
        return keys;
    }

    private DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
        return script;
    }
}
