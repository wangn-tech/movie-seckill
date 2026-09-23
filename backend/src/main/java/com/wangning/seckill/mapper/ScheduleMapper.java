package com.wangning.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangning.seckill.entity.Schedule;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ScheduleMapper extends BaseMapper<Schedule> {

    /** 查所有在售票次（库存对账用） */
    List<Schedule> listActiveSchedules();

    /** 条件扣减库存：WHERE available >= count，返回影响行数 */
    int deductStock(@Param("scheduleId") Long scheduleId, @Param("count") int count);
}
