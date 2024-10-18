package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import jdk.nashorn.internal.ir.IfNode;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {
    @Resource
    private IUserService userService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private IFollowService followService;

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            idBlogLiked(blog);
        });
        return Result.ok(records);
    }

    @Override
    public Result queryBlogById(long id) {
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("笔记不存在!");
        }
        //查询封装
        queryBlogUser(blog);
        //判断是否点赞
        idBlogLiked(blog);
        return Result.ok(blog);
    }

    private void idBlogLiked(Blog blog) {

        String key = BLOG_LIKED_KEY + blog.getId();
        UserDTO user = UserHolder.getUser();
        if (user == null)
            return;
        Long userId = user.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        blog.setIsLike(score != null);
    }

    @Override
    public Result likeBlog(Long id) {
        //判断当前用户是否点赞

        String key = BLOG_LIKED_KEY + id;
        Long userId = UserHolder.getUser().getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());

        if (score == null) {
            //如果未点赞 可以点赞
            //  数据库点赞-1
            boolean isSuccess = update().setSql("liked=liked+1").eq("id", id).update();
            if (isSuccess) {
                //如果更新成功  redis添加
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            boolean isSuccess = update().setSql("liked=liked-1").eq("id", id).update();
            if (isSuccess) {
                //如果更新成功  redis添加
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    //点赞列表
    @Override
    public Result queryBlogLikes(long id) {
        String key = BLOG_LIKED_KEY + id;
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        ArrayList<Long> ids = new ArrayList<>();
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        top5.forEach(v -> {
            Long topId = Long.parseLong(v);
            ids.add(topId);
        });
        //拼接字符串
        String idsStr = StrUtil.join(",", ids);
        List<User> users = userService.query().in("id", ids).last("ORDER BY FIELD(id," + idsStr + ")").list();

        ArrayList<UserDTO> dtos = new ArrayList<>();
        users.forEach(v -> {
            UserDTO dto = new UserDTO();
            BeanUtil.copyProperties(v, dto);
            dtos.add(dto);
        });
        return Result.ok(dtos);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);

        if (!isSuccess) {
            return Result.fail("新增笔记失败");
        }
        //获取所有粉丝
        List<Follow> ids = followService.query().eq("follow_user_id", user.getId()).list();
        System.out.println(ids.toString());
        //推送到所有粉丝
        for (Follow f : ids) {
            //获取粉丝id
            Long userId = f.getUserId();
            String key = "feed:" + userId;
//            推送
            stringRedisTemplate.opsForZSet().add(key, user.getId().toString(), System.currentTimeMillis());
        }

        // 返回id
        return Result.ok(blog.getId());
    }

    //封装blog user相关信息
    private void queryBlogUser(Blog blog) {
        Long userId = blog.getUserId();
        User user = userService.getById(userId);
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }

}
