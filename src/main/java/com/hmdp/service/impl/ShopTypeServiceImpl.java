package com.hmdp.service.impl;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Override
    public Result queryTypeList() {
         //从redis里查询
        String shopTypeList = redisTemplate.opsForValue().get("shop:types");
        JSONArray objects = JSONUtil.parseArray(shopTypeList);
        List<ShopType> shopList = JSONUtil.toList(objects, ShopType.class);

        //判断是否为空
        if (shopList.size()!=0){
            //如果有返回
            System.out.println("不为空");
            return Result.ok(shopList);
        }

        //从数据库中查询
        List<ShopType> typeList = query().orderByAsc("sort").list();
        if (typeList==null){
            return Result.fail("类型列表不存在");
        }
        //放入redis中
        redisTemplate.opsForValue().set("shop:types",JSONUtil.toJsonStr(typeList));

        return Result.ok(typeList);
    }
}
