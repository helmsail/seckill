package com.helmsail.seckill.support.server.user;

import com.helmsail.seckill.support.api.user.UserService;
import com.helmsail.seckill.support.api.user.LoginRequest;
import com.helmsail.seckill.support.api.user.LoginResponse;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 用户 Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class UserDubboServiceImpl implements UserService {

    private final UserBizService userBizService;

    @Override
    public LoginResponse login(LoginRequest request) {
        return userBizService.login(request);
    }
}
