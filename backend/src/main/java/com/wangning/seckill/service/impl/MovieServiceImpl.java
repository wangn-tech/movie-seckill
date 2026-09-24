package com.wangning.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.cache.MultiLevelCacheManager;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Movie;
import com.wangning.seckill.mapper.MovieMapper;
import com.wangning.seckill.service.MovieService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 电影服务实现：读多写少，全部走多级缓存。
 */
@Service
@RequiredArgsConstructor
public class MovieServiceImpl implements MovieService {

    private final MovieMapper movieMapper;
    private final MultiLevelCacheManager cache;
    private final BloomFilterService bloom;

    @Override
    public List<Movie> hotMovies() {
        String key = CacheKeyConstants.movieList(1);
        return cache.getList(key, Movie.class, 600, null,
                () -> movieMapper.selectList(new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .eq(Movie::getDeleted, 0)));
    }

    @Override
    public Movie detail(Long movieId) {
        String key = CacheKeyConstants.movieDetail(movieId);
        return cache.get(key, Movie.class, 600,
                () -> bloom.mightContainMovie(movieId),
                () -> movieMapper.selectById(movieId));
    }

    @Override
    public void onMovieCreated(Long movieId) {
        bloom.addMovie(movieId);
    }
}
