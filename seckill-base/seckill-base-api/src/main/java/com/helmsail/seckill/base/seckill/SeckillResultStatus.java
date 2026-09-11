package com.helmsail.seckill.base.seckill;

/**
 * 秒杀结果状态常量（SeckillResultVO.status 取值）
 */
public final class SeckillResultStatus {

    private SeckillResultStatus() {}

    /** 已受理，等待处理 */
    public static final String PENDING = "pending";

    /** 处理中 */
    public static final String PROCESSING = "processing";

    /** 处理成功（SeckillResultVO.orderNo 携带订单号） */
    public static final String SUCCESS = "success";

    /** 处理失败（SeckillResultVO.reason 携带原因） */
    public static final String FAILED = "failed";
}
