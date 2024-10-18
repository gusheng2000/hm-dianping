package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private CacheClient cacheClient;
    @Override
    public Result queryById(Long id) {
        //缓存穿透
        // Shop shop =
        //        cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.SECONDS);

        //缓存击穿 互斥锁解决方法
     /*   Shop shop =
                cacheClient.queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
*/
        //缓存击穿 逻辑过期解决方法
        //Shop shop = queryWithLogicExpire(id);
        Shop shop=cacheClient.queryWithLogicExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //互斥锁解决缓存击穿
        return Result.ok(shop);
    }

    //处理缓存击穿 诗逻辑过期
    /*
    private Shop queryWithLogicExpire(Long id) {

        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(key);

        //2.判断是否存在
        if (StrUtil.isBlank(shopJson)) {
            //不存在直接返回
            return null;
        }
        //3存在 序列化为对象 判断是否过期
        RedisData redisData = JSONUtil.toBean(shopJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();


        if (expireTime.isAfter(LocalDateTime.now())){
            //4.未过期 直接返回
            return shop;
        }

        //4.2已过期  需要重建缓存
        //5.缓存重建
        //5.1获取互锁  判断是否成功
        String lockKey=LOCK_SHOP_KEY+id;
        boolean isLock = tryLock(lockKey);
        if (isLock){
            //TODO 5.2  获取成功  再次做校验缓存  做doubleCatch 如果还是过期在 开启独立线程  实现缓存重建
//            queryWithLogicExpire(id);
                CATCH_REBUILD_EXEUTOR.submit(()->{
                        //重建缓存
                    try {
                        saveShop2Redis(id,20L);
                    } catch (Exception e) {
                       throw new RuntimeException(e);
                    } finally {
                        //释放锁
                        unLock(lockKey);
                    }
                });
        }
        return shop;
    }

*/

    //缓存击穿互斥锁 代码
    /*private Shop queryWithMutex(Long id) {

        String key = CACHE_SHOP_KEY + id;
        //1.从redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3.命中是否是空值
        if (shopJson != null) {  //如果不是null那一定是空字符串
            return null;
        }
        Shop shop = null;

        try {
            //4.实现缓存重建
            //4.1获取互斥锁
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);        //4.2判断是否获取成功
            if (!isLock) {
                //4.3失败  休眠重试
                Thread.sleep(52);
                //递归重试
                queryWithMutex(id);
            }
            //4.4获取成功    再查询一次是否有缓存有的话不需要再重建缓存  (可能是其他线程刚创建好缓存 ) 根据id查询数据路
            shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

            //4.4.1.判断是否存在
            if (StrUtil.isNotBlank(shopJson)) {
                return JSONUtil.toBean(shopJson, Shop.class);
            }
            //4.4.2命中是否是空值
            if (shopJson != null) {  //如果不是null那一定是空字符串
                return null;
            }
            //模拟延迟
            Thread.sleep(200);
            shop = getById(id);

            if (shop == null) {
                //5.不存在  将空值存入redis 返回错误、
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }
            //6.存在 写入redis 设置过期时间 然后返回
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + RandomUtil.randomInt(1, 5), TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7.释放互斥锁
            unLock(LOCK_SHOP_KEY + id);
        }

        return shop;
    }
*/
    //缓存穿透 代码
    private Shop queryWithPassThrough(Long id) {

        String key = CACHE_SHOP_KEY + id;

        //1.从redis中查询缓存
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);

        //2.判断是否存在
        if (StrUtil.isNotBlank(shopJson)) {
            return JSONUtil.toBean(shopJson, Shop.class);
        }
        //3.命中是否是空值
        if (shopJson != null) {  //如果不是null那一定是空字符串
            return null;
        }

        //4. 根据id查询数据路
        Shop shop = getById(id);
        if (shop == null) {
            //5.不存在  将空值存入redis 返回错误、
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);
            return null;
        }
        //6.存在 写入redis 设置过期时间 然后返回
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL + RandomUtil.randomInt(1, 5), TimeUnit.MINUTES);
        return shop;
    }




    @Override
    @Transactional//开启事务 保持原子性
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为空");
        }
        //1.更新数据库
        updateById(shop);
        //2.删除缓存

        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);
        return null;
    }

}
