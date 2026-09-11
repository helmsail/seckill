package com.helmsail.seckill.base.seckill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀结果（规范化协议，Redis 中以 JSON 存储）
 *
 * 定位键为 traceId，与请求一一对应；由消费端统一创建并写入（处理中/成功/失败），
 * 前端轮询时键不存在即视为处理中（消息待消费）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：见 SeckillResultStatus（pending/processing/success/failed） */
    private String status;

    /** 订单号（status=success 时返回） */
    private String orderNo;

    /** 失败原因（status=failed 时返回） */
    private String reason;
}
