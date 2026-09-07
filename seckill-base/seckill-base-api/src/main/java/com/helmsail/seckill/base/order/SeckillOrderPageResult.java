package com.helmsail.seckill.base.order;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 秒杀订单分页结果
 */
@Data
@AllArgsConstructor
public class SeckillOrderPageResult implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<SeckillOrderDTO> records;
    private long total;
    private long pageNum;
    private long pageSize;
}
