package com.helmsail.seckill.support.api.pay;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.Getter;

/**
 * 支付渠道类型
 */
@Getter
public enum PayChannelType {

    MOCK("mock", "模拟支付"),
    ALIPAY("alipay", "支付宝");

    private final String code;
    private final String desc;

    PayChannelType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 按渠道编码解析（回调路由 / 前端传参用；未知编码拒绝）
     */
    public static PayChannelType byCode(String code) {
        for (PayChannelType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "未知支付渠道: " + code);
    }
}
