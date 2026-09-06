package com.helmsail.seckill.support.api.user;

/**
 * 用户 Dubbo 服务接口
 */
public interface UserService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request);
}
