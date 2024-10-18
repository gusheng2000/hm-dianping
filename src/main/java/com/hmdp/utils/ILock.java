package com.hmdp.utils;

public interface ILock {

    /**
     * 获取锁
     *
     * @param timeoutSec 过期时间
     * @return true  成功   false 失败
     */
    boolean tryLock(long timeoutSec);

    /**
     * 释放锁
     */
    void unLock();
}
