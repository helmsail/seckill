package com.helmsail.seckill.base.order;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * 秒杀订单 Mapper
 */
@Mapper
public interface SeckillOrderMapper extends BaseMapper<SeckillOrder> {
}
