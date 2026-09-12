package com.helmsail.seckill.service.web;

import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.user.LoginRequest;
import com.helmsail.seckill.support.api.user.LoginResponse;
import com.helmsail.seckill.support.api.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * C 端用户 Controller
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    /** 登录/注册为写路径，禁用 Dubbo 自动重试（重试可能重复写入） */
    @DubboReference(retries = 0)
    private UserService userService;

    /**
     * 登录（C 端入口：任意角色均可登录）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(userService.login(request));
    }
}
