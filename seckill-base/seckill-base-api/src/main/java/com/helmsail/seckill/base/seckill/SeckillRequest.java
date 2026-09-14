package com.helmsail.seckill.base.seckill;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 秒杀请求（C 端入参 / MQ 消息体）
 *
 * 前端仅提供 activityNo/skuNo/quantity；userId/traceId 由服务端从上下文注入并覆盖（防伪造），
 * 随消息体传给消费端，作为幂等键与结果键的定位依据。
 */
@Data
public class SeckillRequest {

    @NotBlank(message = "活动编号不能为空")
    private String activityNo;

    @NotBlank(message = "SKU编号不能为空")
    private String skuNo;

    @Min(value = 1, message = "购买数量至少为 1")
    private int quantity = 1;

    /** 用户ID（服务端从登录上下文注入并覆盖，前端无需传） */
    private String userId;

    /** 链路追踪ID（服务端从链路上下文注入并覆盖；消费端用作幂等与结果定位键） */
    private String traceId;
}
