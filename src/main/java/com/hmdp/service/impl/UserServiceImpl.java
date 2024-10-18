package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;


import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;


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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 发送验证码
     *
     * @param session
     * @param phone
     * @return
     */
    @Override
    public Result sendCode(HttpSession session, String phone) {
        //1.校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合返回错误信息
            return Result.fail("手机号格式错误!");
        }
        //3.符合 生成验证码
        String code = RandomUtil.randomNumbers(6);
        //4.生成验证码  保存 redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone, code, 2, TimeUnit.MINUTES);
//        session.setAttribute("code", code);
        //5.发送验证码
        log.debug("发送验证码成功! ,验证码:{}", code);


        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //1.校验手机号
        String phone = loginForm.getPhone();
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合返回错误信息
            return Result.fail("手机号格式错误!");
        }
        //2.校验验证码
//        String cacheCode = (String) session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);

        String code = loginForm.getCode();
        //3.不一致报错
        if (cacheCode == null || !cacheCode.equals(code)) {
            return Result.fail("验证错误");
        }
        //4.根据手机号查询数据库
        User user = query().eq("phone", phone).one();

        //5.判断用户是否存在
        if (user == null) {
            //6.不存在创建新用户并保存
            user = createUserByPhone(phone);
        }
        // 7.保存用户信息到redis里
        //7.1随机生成token 当作登录令牌
        String token = UUID.randomUUID().toString(false);
        //  7.2将user对象转换为map存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);


        Map<String, Object> map = BeanUtil.beanToMap(userDTO,new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName,FieldValue)->FieldValue.toString()));
        String tokenKey = RedisConstants.LOGIN_USER_KEY+token;

        //7.2 存储   将user信息存放在redis里 并设置过期时间
        stringRedisTemplate.opsForHash().putAll(tokenKey, map);
        stringRedisTemplate.expire(tokenKey, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        //8。将token返回给前端
        return Result.ok(token);
    }

    private User createUserByPhone(String phone) {

        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomNumbers(10));
        //保存用户到数据库
        save(user);
        return user;
    }
}
