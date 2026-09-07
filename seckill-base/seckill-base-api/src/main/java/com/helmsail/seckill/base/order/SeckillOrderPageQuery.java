package com.helmsail.seckill.base.order;

import lombok.Data;

import java.io.Serializable;

/**
 * 秒杀订单分页查询
 */
@Data
public class SeckillOrderPageQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户 ID */
    private Long userId;

    /** 当前页码 */
    private Integer pageNum = 1;

    /** 每页条数 */
    private Integer pageSize = 10;
}
