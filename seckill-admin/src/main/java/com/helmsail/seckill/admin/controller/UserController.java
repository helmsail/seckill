package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.user.Role;
import com.helmsail.seckill.support.api.user.LoginRequest;
import com.helmsail.seckill.support.api.user.LoginResponse;
import com.helmsail.seckill.support.api.user.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端登录 Controller
 */
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    @DubboReference
    private UserService userService;

    /**
     * 登录（管理端入口：仅运营人员可登录）
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        Integer roleCode = response.getUser() == null ? null : response.getUser().getRole();
        if (Role.fromCode(roleCode) != Role.OPERATOR) {
            throw new BizException(ResultEnum.FORBIDDEN.getCode(), "仅运营人员可登录管理后台");
        }
        return Result.success(response);
    }
}
