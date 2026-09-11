package com.helmsail.seckill.support.api.pay;

import lombok.Data;

import java.io.Serializable;

/**
 * 异步通知验签结果
 */
@Data
public class PayNotifyResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 验签是否通过 */
    private boolean valid;

    /** 商户订单号 */
    private String outTradeNo;

    /** 交易号（流水号） */
    private String tradeNo;

    /** 交易状态（见 PayTradeStatus） */
    private String tradeStatus;

    /** 金额（单位：元） */
    private String totalAmount;
}
