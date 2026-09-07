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
}
