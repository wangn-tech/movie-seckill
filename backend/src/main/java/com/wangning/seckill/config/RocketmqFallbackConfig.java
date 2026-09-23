package com.wangning.seckill.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RocketMQ 不可用时提供空 Producer，避免启动失败（压测/开发环境用）。
 */
@Configuration
public class RocketmqFallbackConfig {

    @Bean
    @ConditionalOnMissingBean(DefaultMQProducer.class)
    public DefaultMQProducer fallbackProducer() {
        DefaultMQProducer p = new DefaultMQProducer("fallback-group");
        p.setNamesrvAddr("127.0.0.1:9876");
        try {
            p.start();
        } catch (Exception e) {
            // 启动失败不阻断，send 时会报错但不影响非 MQ 路径
        }
        return p;
    }
}
