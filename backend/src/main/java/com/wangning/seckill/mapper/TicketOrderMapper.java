package com.wangning.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangning.seckill.entity.TicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TicketOrderMapper extends BaseMapper<TicketOrder> {

    List<TicketOrder> selectOrdersByUser(@Param("userId") Long userId);

    /** 订单状态 CAS：expect -> target，返回影响行数 */
    int casUpdateStatus(@Param("orderNo") String orderNo,
                        @Param("userId") Long userId,
                        @Param("expect") int expect,
                        @Param("target") int target);

    int countSoldBySchedule(@Param("scheduleId") Long scheduleId);
}
