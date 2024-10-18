package com.hmdp.utils;


import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


/**
 * @Author sc
 * @ClassName LoginInterceprot
 * @Description class function:
 * @Date 2022/10/22 12:51:21
 **/
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //1.判断是否需要拦截  threadLocal中是否有用户
      if ( UserHolder.getUser()==null){
          //没有用户  拦截
          response.setStatus(401);
          return false;
      }

      //有用户 则放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
