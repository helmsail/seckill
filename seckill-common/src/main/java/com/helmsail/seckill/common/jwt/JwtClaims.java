package com.helmsail.seckill.common.jwt;

/**
 * JWT claims 契约常量
 *
 * 签发（support）与校验（网关）共用，避免两侧裸字符串写岔。
 */
public final class JwtClaims {

    private JwtClaims() {}

    /** 用户 ID */
    public static final String USER_ID = "userId";

    /** 用户名 */
    public static final String USERNAME = "username";

    /** 角色（0=C端用户，1=运营人员） */
    public static final String ROLE = "role";
}
