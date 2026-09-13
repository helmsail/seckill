package com.helmsail.seckill.common.user;

import lombok.Getter;

/**
 * 用户角色枚举
 *
 * 与 t_user.role（TINYINT）对齐，全链路单点定义：
 * DB TINYINT ⇄ 业务 Integer（UserDTO）⇄ JWT String（claim）⇄ 网关 String。
 * 各层转换统一走 byCode/fromCode/fromCodeOrNull，避免裸写 "1" 等字面量。
 */
@Getter
public enum Role {

    CONSUMER(0, "C端用户"),
    OPERATOR(1, "运营人员");

    private final int code;
    private final String desc;

    Role(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按 code 查找角色，未知 code 抛异常（用于 DB/JWT 等可信来源）
     */
    public static Role byCode(int code) {
        for (Role role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知角色码: " + code);
    }

    /**
     * 按 code 查找角色，未知返回 null（用于外部传入值的安全判断，不抛异常）
     */
    public static Role fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (Role role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        return null;
    }

    /**
     * 按字符串 code 查找角色，未知返回 null（用于 JWT claim 解析）
     */
    public static Role fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        try {
            return fromCode(Integer.parseInt(code.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** JWT claim 中使用的字符串形式（如 "1"），与签发/校验两侧对齐 */
    public String getClaimValue() {
        return String.valueOf(code);
    }
}
