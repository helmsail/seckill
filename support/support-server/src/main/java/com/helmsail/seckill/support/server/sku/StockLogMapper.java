package com.helmsail.seckill.support.server.sku;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * SKU 库存变更流水 Mapper（幂等闸）
 */
@Mapper
public interface StockLogMapper extends BaseMapper<StockLog> {
}
