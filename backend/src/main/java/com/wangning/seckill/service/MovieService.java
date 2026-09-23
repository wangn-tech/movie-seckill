package com.wangning.seckill.service;

import com.wangning.seckill.entity.Movie;

import java.util.List;

/**
 * 电影服务接口：读多写少，全部走多级缓存。
 */
public interface MovieService {

    /** 正在热映电影列表 */
    List<Movie> hotMovies();

    /** 电影详情 */
    Movie detail(Long movieId);

    /** 新增电影时灌入布隆过滤器 */
    void onMovieCreated(Long movieId);
}
