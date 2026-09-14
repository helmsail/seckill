package com.helmsail.seckill.base.seckill;

/**
 * 秒杀结果状态常量（SeckillResultVO.status 取值）
 *
 * 说明：不存在"已受理待处理"的显式状态——消息待消费期间结果键缺失（轮询返回 null），
 * 键存在即为处理中或终态；受理时写占位会与结果键兼任的幂等闸门（SETNX）冲突。
 */
public final class SeckillResultStatus {

    private SeckillResultStatus() {}

    /** 处理中 */
    public static final String PROCESSING = "processing";

    /** 处理成功（SeckillResultVO.orderNo 携带订单号） */
    public static final String SUCCESS = "success";

    /** 处理失败（SeckillResultVO.reason 携带原因） */
    public static final String FAILED = "failed";
}
