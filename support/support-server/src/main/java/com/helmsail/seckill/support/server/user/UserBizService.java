package com.helmsail.seckill.support.server.user;

import com.helmsail.seckill.support.api.user.LoginRequest;
import com.helmsail.seckill.support.api.user.LoginResponse;

/**
 * 用户内部服务接口
 */
public interface UserBizService {

    LoginResponse login(LoginRequest request);
}
