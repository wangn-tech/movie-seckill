package com.wangning.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangning.seckill.entity.SeatLock;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeatLockMapper extends BaseMapper<SeatLock> {

    List<SeatLock> selectActiveBySchedule(@Param("scheduleId") Long scheduleId);

    List<SeatLock> selectExpiredLocks();
}
