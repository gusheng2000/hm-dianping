package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IUserService;
import com.hmdp.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private IUserService userService;

    @Override
    public Result follow(Long followUserId, boolean isFollow) {

        Long id = UserHolder.getUser().getId();
//        System.out.println(id.toString()+"   =>>>>>>>>>>>>>>>>>>");
        String key ="follow:"+id;
        //判断是关注还是取关
        if (isFollow) {
            //   1.如果关注 新增数据
            Follow follow = new Follow();
            follow.setUserId(id);
            follow.setFollowUserId(followUserId);
            boolean isSuccess = save(follow);
            if (isSuccess){
                //放入redis set 集合
                stringRedisTemplate.opsForSet().add(key,followUserId.toString());
            }

        } else {
            //2.如果取关  删除数据
            boolean isSuccess = remove(new QueryWrapper<Follow>().
                    eq("user_id", id)
                    .eq("follow_user_id", followUserId));
            if (isSuccess){
                stringRedisTemplate.opsForSet().remove(key,followUserId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result isFollow(Long followUserId) {
        //获取登录用户
        Long id = UserHolder.getUser().getId();
        //查询是否有
        Integer count = query().eq("user_id", id)
                .eq("follow_user_id", followUserId).count();
        // 返回
        return Result.ok(count > 0);
    }

    @Override
    public Result commonFollow(long followUserId) {
        //获取当前userId
        Long id = UserHolder.getUser().getId();
        String k1 ="follow:"+followUserId;
        String k2 ="follow:"+id;

        //查询是否有交集
        Set<String> ids = stringRedisTemplate.opsForSet().intersect(k1, k2);
        if (ids==null||ids.isEmpty()){
            return Result.ok(Collections.emptyList());
        }
        //有交集 查询用户信息 封装成userDTO返回
            //转换为long
        List<Long> list = ids.stream().map(Long::valueOf).collect(Collectors.toList());

        //查询相关用户信息
        List<User> users = userService.listByIds(list);
        List<UserDTO> dtos = users.stream().map(v -> BeanUtil.copyProperties(v, UserDTO.class)).collect(Collectors.toList());
        return Result.ok(dtos);
    }
}
