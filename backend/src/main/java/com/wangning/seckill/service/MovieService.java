package com.wangning.seckill.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wangning.seckill.cache.BloomFilterService;
import com.wangning.seckill.cache.MultiLevelCacheManager;
import com.wangning.seckill.common.constant.CacheKeyConstants;
import com.wangning.seckill.entity.Movie;
import com.wangning.seckill.mapper.MovieMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 电影服务：读多写少，全部走多级缓存。
 */
@Service
@RequiredArgsConstructor
public class MovieService {

    private final MovieMapper movieMapper;
    private final MultiLevelCacheManager cache;
    private final BloomFilterService bloom;

    /** 正在热映电影列表 */
    public List<Movie> hotMovies() {
        String key = CacheKeyConstants.movieList(1);
        return cache.get(key, List.class, 600, null,
                () -> movieMapper.selectList(new LambdaQueryWrapper<Movie>()
                        .eq(Movie::getStatus, 1)
                        .eq(Movie::getDeleted, 0)));
    }

    /** 电影详情 */
    public Movie detail(Long movieId) {
        String key = CacheKeyConstants.movieDetail(movieId);
        return cache.get(key, Movie.class, 600,
                () -> bloom.mightContainMovie(movieId),
                () -> movieMapper.selectById(movieId));
    }

    /** 新增电影时灌入布隆过滤器 */
    public void onMovieCreated(Long movieId) {
        bloom.addMovie(movieId);
    }
}
