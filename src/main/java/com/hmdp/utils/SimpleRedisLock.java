package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @Author sc
 * @ClassName SimpleRedisLock
 * @Description class function:
 * @Date 2022/10/30 10:06:27
 **/

public class SimpleRedisLock implements ILock {
    private String name;
    private StringRedisTemplate stringRedisTemplate;
    private static final String key_prefix = "lock:";
    //防止线程id重复造成线程安全问题
    private static final String ID_prefix = UUID.randomUUID().toString(true) + "-";


    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT=new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unLock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //线程标识
        String threadId = ID_prefix + Thread.currentThread().getId();

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key_prefix + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);

    }

    @Override
    public void unLock() {
        //调用LUA脚本

        stringRedisTemplate.execute(UNLOCK_SCRIPT,
                Collections.singletonList(key_prefix+name),
                ID_prefix + Thread.currentThread().getId()
                );
    }

    /*@Override
    public void unLock() {
        String threadId =ID_prefix+ Thread.currentThread().getId();

        String id = stringRedisTemplate.opsForValue().get(key_prefix + name);
        if (threadId.equals(id)){
            stringRedisTemplate.delete(key_prefix + name);
        }
    }*/
}
