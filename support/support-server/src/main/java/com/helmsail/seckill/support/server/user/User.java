package com.helmsail.seckill.support.server.user;

import com.helmsail.seckill.common.mybatis.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户实体
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class User extends BaseEntity {

    private Long id;
    private String username;
    private String password;
    private Integer role;
}
