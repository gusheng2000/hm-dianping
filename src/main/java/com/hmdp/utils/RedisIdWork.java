package com.hmdp.utils;

import com.fasterxml.jackson.core.format.DataFormatMatcher;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

/**
 * @Author sc
 * @ClassName RedisIdWork
 * @Description class function: 全局ID生成器
 * @Date 2022/10/29 14:42:08
 **/
@Component
public class RedisIdWork {
    private static final long BEGIN_TIMESTAMP = 1642809600;
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate redisTemplate;

    public RedisIdWork(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long nextId(String keyPrefix) {
        //生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timeStamp = nowSecond - BEGIN_TIMESTAMP;
        //生成序列号
        //获取当前时间,精确到天
        //yyyy:MM:dd 这种格式方便统计
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        //自增长
        Long count = redisTemplate.opsForValue().increment("inc:" + keyPrefix + ":" + date);
        System.out.println(count);
        //拼接  先进行位运算  左移 在和count进行或运算
        return timeStamp << COUNT_BITS | count;
    }

    public static void main(String[] args) {
        LocalDateTime time = LocalDateTime.of(2022, 1, 22, 0, 0, 0);
        long second = time.toEpochSecond(ZoneOffset.UTC);
        System.out.println(second);
    }
}
