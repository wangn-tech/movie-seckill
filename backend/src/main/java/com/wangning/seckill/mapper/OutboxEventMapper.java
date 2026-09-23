package com.wangning.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wangning.seckill.entity.OutboxEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OutboxEventMapper extends BaseMapper<OutboxEvent> {
}
