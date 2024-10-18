package com.hmdp.config;

import io.lettuce.core.RedisClient;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @Author sc
 * @ClassName RedissonConfig
 * @Description class function:
 * @Date 2022/11/2 16:52:49
 **/
@Configuration
public class RedissonConfig {

    @Bean
    public RedissonClient redissonClient() {
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://www.gusheng.work:3307").setPassword("123321");
        //创建RedissonClient 对象
        return Redisson.create(config);
    }

//    @Bean
    public RedissonClient redissonClient2() {
        //配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://192.168.222.128:6379").setPassword("123321");
        //创建RedissonClient 对象
        return Redisson.create(config);
    }
}
