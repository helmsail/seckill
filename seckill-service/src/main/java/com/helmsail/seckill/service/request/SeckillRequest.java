package com.helmsail.seckill.service.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SeckillRequest {

    @NotBlank(message = "活动编号不能为空")
    private String activityNo;

    @NotBlank(message = "SKU 编号不能为空")
    private String skuNo;

    @Min(value = 1, message = "购买数量至少为 1")
    private int quantity = 1;
}
