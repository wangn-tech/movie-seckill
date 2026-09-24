package com.wangning.seckill.controller;

import com.wangning.seckill.common.result.Result;
import com.wangning.seckill.service.CinemaService;
import com.wangning.seckill.vo.CinemaVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "影院")
@RestController
@RequestMapping("/cinemas")
@RequiredArgsConstructor
public class CinemaController {

    private final CinemaService cinemaService;

    @Operation(summary = "影院列表")
    @GetMapping
    public Result<List<CinemaVO>> list() {
        return Result.success(cinemaService.list().stream().map(CinemaVO::from).toList());
    }
}
