package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

/**
 * @Author sc
 * @ClassName CatchcCient
 * @Description class function:
 * @Date 2022/10/25 18:13:15
 **/
@Slf4j
@Component
public class CacheClient {
    //通过构造方法注入
    private final StringRedisTemplate stringRedisTemplate;
    private static final ExecutorService CATCH_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);

    public CacheClient(StringRedisTemplate redisTemplate) {
        this.stringRedisTemplate = redisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit) {
        //序列化jsonStr
        String jsonStr = JSONUtil.toJsonStr(value);
        stringRedisTemplate.opsForValue().set(key, jsonStr, time, unit);
    }

    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {

        ///封装redisData对象
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //序列化jsonStr
        String jsonStr = JSONUtil.toJsonStr(redisData);
        stringRedisTemplate.opsForValue().set(key, jsonStr);
    }

    //缓存穿透 代码
    public <R, ID> R queryWithPassThrough(
            String keyPreFix, ID id, Class<R> type, Function<ID, R> dbFailBack, Long time, TimeUnit unit) {

        String key = keyPreFix + id;

        //1.从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isNotBlank(json)) {
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        //3.命中是否是空值
        if (json != null) {  //如果不是null那一定是空字符串
            log.debug("返回Null");
            return null;
        }

        //4. 根据id查询数据路  函数式编程 传方法
        R r = dbFailBack.apply(id);
        if (r == null) {
            //5.不存在  将空值存入redis 返回错误、
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在 写入redis 设置过期时间 然后返回
//        redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(r), time,unit);
        this.set(key, r, time, unit);
        return r;
    }


    //处理缓存击穿 诗逻辑过期
    public <R, ID> R queryWithLogicExpire(
            String keyPreFix, ID id, Class<R> type, Function<ID, R> dbFailBack, Long time, TimeUnit unit) {

        String key = keyPreFix + id;
        //1.从redis中查询缓存
        String json = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isBlank(json)) {
            //不存在直接返回
            return null;
        }
        //3存在 序列化为对象 判断是否过期
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();


        if (expireTime.isAfter(LocalDateTime.now())) {
            //4.未过期 直接返回
            return r;
        }
        //4.2已过期  需要重建缓存
        //5.缓存重建
        //5.1获取互锁  判断是否成功
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (isLock) {
            //TODO 5.2  获取成功  再次做校验缓存  做doubleCatch 如果还是过期在 开启独立线程  实现缓存重建
//            queryWithLogicExpire(id);
            CATCH_REBUILD_EXECUTOR.submit(() -> {
                //重建缓存
                try {
                    //查询数据库
                    R r1 = dbFailBack.apply(id);
                    //写入缓存
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unLock(lockKey);
                }
            });
        }
        return r;
    }


    //获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);
    }

    //释放锁
    private void unLock(String key) {
        stringRedisTemplate.delete(key);
    }

}
