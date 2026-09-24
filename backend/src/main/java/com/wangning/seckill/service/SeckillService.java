package com.wangning.seckill.service;

import com.wangning.seckill.dto.SeckillReq;
import com.wangning.seckill.vo.SeckillStatusVO;

/**
 * 抢座核心服务接口。
 */
public interface SeckillService {

    /**
     * 抢座入口。
     * @return requestId（前端轮询订单状态用）
     */
    SeckillStatusVO seize(Long userId, SeckillReq req);

    SeckillStatusVO status(Long userId, String requestId);

    /** 启动时把场次库存预热到 Redis */
    void preloadStock(Long scheduleId, int stock);
}
