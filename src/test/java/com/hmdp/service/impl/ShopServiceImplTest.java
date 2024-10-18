package com.hmdp.service.impl;

import com.hmdp.HmDianPingApplication;
import com.hmdp.entity.Shop;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisIdWork;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.concurrent.*;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;


@SpringBootTest(classes = HmDianPingApplication.class)
@RunWith(SpringRunner.class)
public class ShopServiceImplTest {

    @Autowired
    private ShopServiceImpl shopService;
    @Autowired
    private CacheClient cacheClient;
    @Autowired
    private RedisIdWork redisIdWork;

    private ExecutorService es= Executors.newFixedThreadPool(500);

    @Test
    public void testIdWork() throws InterruptedException {
        CountDownLatch latch=new CountDownLatch(300);

        Runnable tesk=()->{
            for (int i = 0; i <100 ; i++) {
                long id = redisIdWork.nextId("order");
                System.out.println("id= "+id);
            }
            latch.countDown();
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i <300 ; i++) {
                es.submit(tesk);
        }
        latch.await();

        long end = System.currentTimeMillis();

        System.out.println("time+  " +(end-begin));

    }


    @Test
    public void testServer() throws InterruptedException {
        Shop shop = shopService.getById(1L);
        cacheClient.setWithLogicalExpire(CACHE_SHOP_KEY + 1L, shop, 10L, TimeUnit.SECONDS);
    }


}