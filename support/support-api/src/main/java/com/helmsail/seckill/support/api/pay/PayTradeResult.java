package com.helmsail.seckill.support.api.pay;

import lombok.Data;

import java.io.Serializable;

/**
 * 交易查询结果
 */
@Data
public class PayTradeResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 商户订单号 */
    private String outTradeNo;

    /** 交易号（流水号） */
    private String tradeNo;

    /** 交易状态（见 PayTradeStatus） */
    private String tradeStatus;

    /** 金额（单位：元） */
    private String totalAmount;

    /** 支付时间 */
    private String payTime;
}
