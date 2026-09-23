package com.wangning.seckill.controller;

import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.entity.Movie;
import com.wangning.seckill.service.MovieService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "电影")
@RestController
@RequestMapping("/movies")
@RequiredArgsConstructor
public class MovieController {

    private final MovieService movieService;

    @Operation(summary = "正在热映电影列表")
    @GetMapping("/hot")
    public Result<List<Movie>> hot() {
        return Result.success(movieService.hotMovies());
    }

    @Operation(summary = "电影详情")
    @GetMapping("/{id}")
    public Result<Movie> detail(@PathVariable Long id) {
        return Result.success(movieService.detail(id));
    }
}
