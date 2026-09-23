package com.wangning.seckill;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 电影票秒杀系统启动类。
 *
 * <p>技术栈：Java 21 + Spring Boot 3.3 + MyBatis-Plus + MySQL + Redis + RocketMQ + Caffeine + Outbox。
 *
 * <p>核心链路：Redis Lua 原子抢座预扣库存 → RocketMQ 异步落单 → MySQL 条件更新 + 唯一索引兜底。
 */
@EnableAsync
@EnableScheduling
@MapperScan("com.wangning.seckill.mapper")
@SpringBootApplication
public class SeckillApplication {

    public static void main(String[] args) {
        SpringApplication.run(SeckillApplication.class, args);
    }
}
