package com.helmsail.seckill.support.api.user;

import com.helmsail.seckill.common.exception.BizException;

/**
 * 用户 Dubbo 服务接口
 */
public interface UserDubboService {

    /**
     * 用户登录
     */
    LoginResponse login(LoginRequest request) throws BizException;
}
