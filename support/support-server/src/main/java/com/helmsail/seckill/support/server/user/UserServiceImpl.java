package com.helmsail.seckill.support.server.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.jwt.JwtClaims;
import com.helmsail.seckill.common.jwt.JwtUtils;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.common.user.Role;
import com.helmsail.seckill.support.api.result.SupportResultEnum;
import com.helmsail.seckill.support.api.user.LoginRequest;
import com.helmsail.seckill.support.api.user.LoginResponse;
import com.helmsail.seckill.support.api.user.UserDTO;
import com.helmsail.seckill.support.api.user.UserService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 用户服务实现（Dubbo 暴露）
 */
@Service
@DubboService
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final JwtUtils jwtUtils;

    @Override
    public LoginResponse login(LoginRequest request) {
        if (request.getUsername() == null || request.getPassword() == null) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (user == null) {
            throw new BizException(SupportResultEnum.USER_NOT_FOUND);
        }
        if (!user.getPassword().equals(request.getPassword())) {
            throw new BizException(SupportResultEnum.USER_PASSWORD_ERROR);
        }
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setUsername(user.getUsername());
        dto.setRole(user.getRole());
        String token = jwtUtils.generateToken(Map.of(
                JwtClaims.USER_ID, String.valueOf(user.getId()),
                JwtClaims.USERNAME, user.getUsername(),
                JwtClaims.ROLE, Role.byCode(user.getRole()).getClaimValue()));
        return new LoginResponse(token, dto);
    }
}
