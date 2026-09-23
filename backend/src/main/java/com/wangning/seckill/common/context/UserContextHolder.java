package com.wangning.seckill.common.context;

/**
 * 基于 ThreadLocal 的用户上下文。
 *
 * <p>JWT 拦截器认证成功后把 userId 塞进来，Controller/Service 直接取，无需在方法参数里反复传。
 * 拦截器在 afterCompletion 必须调用 {@link #clear()}，否则线程池复用会导致用户串号。
 *
 * <p>注意：异步线程、MQ 消费线程不继承父线程 ThreadLocal，需要显式传 userId。
 */
public final class UserContextHolder {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private UserContextHolder() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    public static Long getUserId() {
        return USER_ID.get();
    }

    public static Long requireUserId() {
        Long uid = USER_ID.get();
        if (uid == null) {
            throw new IllegalStateException("用户未登录");
        }
        return uid;
    }

    public static void clear() {
        USER_ID.remove();
    }
}
