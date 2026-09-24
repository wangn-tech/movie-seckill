package com.wangning.seckill.common.constant;

/**
 * Redis Key 统一前缀管理，避免散落在代码里。
 */
public final class CacheKeyConstants {

    private CacheKeyConstants() {
    }

    /** 场次库存：seckill:stock:{scheduleId} */
    public static String stockKey(Long scheduleId) {
        return "seckill:stock:" + scheduleId;
    }

    /** 抢座一次性凭证：seckill:reservation:{requestId} */
    public static String reservationKey(String requestId) {
        return "seckill:reservation:" + requestId;
    }

    /** 库存释放幂等标记 */
    public static String releaseMarker(String requestId) {
        return "seckill:released:" + requestId;
    }

    /** 单座锁：seckill:seat:{scheduleId}:{row}-{col} */
    public static String seatKey(Long scheduleId, int row, int col) {
        return "seckill:seat:" + scheduleId + ":" + row + "-" + col;
    }

    /** 电影详情缓存 */
    public static String movieDetail(Long movieId) {
        return "movie:detail:" + movieId;
    }

    /** 电影列表缓存（按状态） */
    public static String movieList(int status) {
        return "movie:list:" + status;
    }

    /** 影院列表缓存 */
    public static String cinemaList() {
        return "cinema:list";
    }

    /** 某电影的场次列表 */
    public static String scheduleList(Long movieId, Long cinemaId) {
        return "schedule:list:" + movieId + ":" + cinemaId;
    }

    /** 某场次的座位图 */
    public static String seatLayout(Long scheduleId) {
        return "seat:layout:" + scheduleId;
    }

    /** 布隆过滤器：所有合法 scheduleId */
    public static String BLOOM_SCHEDULE = "bloom:schedule";

    /** 布隆过滤器：所有合法 movieId */
    public static String BLOOM_MOVIE = "bloom:movie";

    /** 分布式锁：缓存重建 */
    public static String rebuildLock(String key) {
        return "lock:rebuild:" + key;
    }

    /** 令牌桶限流：rate:{userId}:{api} */
    public static String rateLimitKey(String userId, String api) {
        return "rate:" + userId + ":" + api;
    }

    /** RefreshToken 在 Redis 中的存储 */
    public static String refreshToken(Long userId) {
        return "auth:refresh:" + userId;
    }
}
