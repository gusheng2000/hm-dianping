package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWork;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Collections;
import java.util.concurrent.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //阻塞队列
    private BlockingQueue<VoucherOrder> ordersTasks = new ArrayBlockingQueue<>(1024 * 1024);
    //线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //类初始化后执行
    @PostConstruct
    private void  init(){
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true){
                VoucherOrder order = null;
                try {
                    order = ordersTasks.take();
                    handlerVoucherOrder(order);
                } catch (InterruptedException e) {
                    log.error("处理订单异常",e);
                }
            }
        }
    }


    //加载lua脚本
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Autowired
    private RedisIdWork redisIdWork;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;


    private IVoucherOrderService  proxy;
    @Override
    public Result secKillVoucher(Long voucherId) {
//        //1.查询优惠券
//        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
//
//        //2.判断秒杀是否开始
//        LocalDateTime beginTime = voucher.getBeginTime();
//        if (beginTime.isAfter(LocalDateTime.now())) {
//            //如果还没开始
//            return Result.fail("秒杀尚未开始!");
//        }
//        //3.判断秒杀是否结束
//        LocalDateTime endTime = voucher.getEndTime();
//        if (endTime.isBefore(LocalDateTime.now())) {
//            //如果已经结束
//            return Result.fail("秒杀已经结束!");
//        }
//        //4.判断库存是否充足
//        if (voucher.getStock() < 1) {
//            return Result.fail("库存不足!");
//        }
//        Long userId = UserHolder.getUser().getId();
//        //单体架构解决一人一单  锁当前用户  性能提高
////        synchronized (userId.toString().intern()) {
////            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
////            return proxy.createVoucherOrder(voucherId);
////        }
//        //分布式架构解决跨jvm解决一人一单  锁当前用户  --->分布式锁
//
//        //创建锁对象
////        SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        RLock lock = redissonClient.getLock("lock:order:" + userId);
//        boolean isLock = lock.tryLock();
//
//        if (!isLock) {
//           //获取成功失败,返回错误或重试()
//            return Result.fail("不允许重复下单");
//        }
//        try {
//            //获取成功 执行业务
//            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
//            return proxy.createVoucherOrder(voucherId);
//        } finally {
//            //释放锁
//            lock.unlock();
//        }
        //获取用户
        Long userId = UserHolder.getUser().getId();
        //1执行lua脚本
        Long r = stringRedisTemplate.execute(SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString());
        //2.判断返回数据
        if (r.intValue() != 0) {
            //2.1  不为0  没有购买资格
            return Result.fail(r == 1 ? "库存不足" : "不允许重复下单");
        }
        //2.2为0 有购买资格 下单保存到阻塞队列
        //TODO  保存到阻塞队列

        //创建订单
        VoucherOrder order = new VoucherOrder();
        long orderId = redisIdWork.nextId("order");
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setId(orderId);
//        放入阻塞队列
        ordersTasks.add(order);

        //获取代理对象
         proxy = (IVoucherOrderService) AopContext.currentProxy();

        return Result.ok(0);
    }

    private void handlerVoucherOrder(VoucherOrder order) {
        Long userId = order.getUserId();
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean isLock = lock.tryLock();
        log.debug("获取成功 执行业务->2");
        if (!isLock) {
           //获取成功失败,返回错误或重试()
           log.error("不允许重复下单");
        }
        try {
            log.debug("获取成功 执行业务->1");
            //获取成功 执行业务
             proxy.createVoucherOrder(order);
        } finally {
            //释放锁
            lock.unlock();
        }


    }
    @Override
    @Transactional
    public void createVoucherOrder(VoucherOrder order) {
        log.debug("获取成功 执行业务->>");
        //一人一单
        Long userId = order.getId();

        //a.查询订单
        Integer count = query().eq("user_id", userId).eq("voucher_id", order.getVoucherId()).count();
        //b.判断是否存在
        if (count > 0) {
           log.error("一人只可购买一次!");
           return;
        }

        //5.扣除库存  乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock=stock -1")
                .eq("voucher_id", order.getVoucherId())
                .gt("stock", 0).update();
        if (!success) {
            log.error("一人只可购买一次!");
            return;
        }

//        //创建订单
//        VoucherOrder order = new VoucherOrder();
//        long orderId = redisIdWork.nextId("order");
//        order.setUserId(userId);
//        order.setVoucherId(voucherId);
//        order.setId(orderId);

        //保存
        save(order);
        //返回订单id
//        return Result.ok(orderId);
    }
}
