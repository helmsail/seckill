package com.helmsail.seckill.support.api.pay;

import lombok.Data;

import java.io.Serializable;

/**
 * 支付请求
 */
@Data
public class PayRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商品标题 */
    private String subject;

    /** 商户订单号 */
    private String outTradeNo;

    /** 金额（单位：元） */
    private String totalAmount;

    /** 支付超时（分钟）：与订单关单时间对齐，各渠道按自身格式转换（如支付宝 "10m"） */
    private Integer timeoutMinutes;
}
