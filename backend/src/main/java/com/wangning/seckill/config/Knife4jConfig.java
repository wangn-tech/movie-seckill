package com.wangning.seckill.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / OpenAPI3 接口文档配置。
 * 启动后访问 http://localhost:8080/doc.html
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI seckillOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("电影票秒杀系统 API")
                        .description("Java21 + SpringBoot3 + Redis + RocketMQ + Caffeine + Outbox 高并发秒杀演示")
                        .version("v1.0.0"));
    }
}
