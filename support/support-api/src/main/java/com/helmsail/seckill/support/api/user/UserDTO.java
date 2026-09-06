package com.helmsail.seckill.support.api.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户 DTO
 */
@Data
public class UserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String username;
    private Integer role;
}
