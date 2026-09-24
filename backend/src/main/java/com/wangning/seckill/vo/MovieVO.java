package com.wangning.seckill.vo;

import com.wangning.seckill.entity.Movie;

import java.math.BigDecimal;

/** 对外电影读模型，隔离 MyBatis-Plus 持久化字段。 */
public record MovieVO(
        Long id,
        String name,
        String poster,
        BigDecimal score,
        String actors,
        String genre,
        Integer duration,
        String description,
        Integer status
) {
    public static MovieVO from(Movie movie) {
        if (movie == null) {
            return null;
        }
        return new MovieVO(movie.getId(), movie.getName(), movie.getPoster(), movie.getScore(),
                movie.getActors(), movie.getGenre(), movie.getDuration(), movie.getDescription(), movie.getStatus());
    }
}
