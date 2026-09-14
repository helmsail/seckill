package com.helmsail.seckill.base.seckill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 秒杀结果（规范化协议，Redis 中以 JSON 存储）
 *
 * 定位键为 traceId，与请求一一对应；由消费端统一写入（processing/success/failed）。
 * 前端轮询契约：键不存在（null）= 尚未处理或已过期；processing = 处理中；
 * success = 成功（携带 orderNo）；failed = 失败（携带 reason）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态：见 SeckillResultStatus（processing/success/failed） */
    private String status;

    /** 订单号（status=success 时返回） */
    private String orderNo;

    /** 失败原因（status=failed 时返回） */
    private String reason;
}
