package com.wangning.seckill.controller;

import com.wangning.seckill.common.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 健康检查接口。
 */
@RestController
@RequestMapping("/health")
public class HealthController {

    @GetMapping
    public Result<Map<String, Object>> health() {
        Map<String, Object> m = new HashMap<>();
        m.put("status", "UP");
        m.put("app", "movie-seckill-backend");
        m.put("java", System.getProperty("java.version"));
        return Result.success(m);
    }
}
