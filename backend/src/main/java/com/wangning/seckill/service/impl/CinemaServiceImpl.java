package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.cache.MultiLevelCacheManager;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Cinema;
import com.wangning.seckill.mapper.CinemaMapper;
import com.wangning.seckill.service.CinemaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CinemaServiceImpl implements CinemaService {

    private final CinemaMapper cinemaMapper;
    private final MultiLevelCacheManager cache;

    @Override
    public List<Cinema> list() {
        return cache.get(CacheKeyConstants.cinemaList(), List.class, 600, null,
                () -> cinemaMapper.selectList(new LambdaQueryWrapper<>()));
    }
}
