package com.helmsail.seckill.support.server.sku;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * SKU Mapper
 */
@Mapper
public interface SkuMapper extends BaseMapper<Sku> {

    @Update("UPDATE t_sku SET stock = stock - #{quantity} WHERE sku_no = #{skuNo} AND stock >= #{quantity} AND is_deleted = 0")
    int deductStock(@Param("skuNo") String skuNo, @Param("quantity") int quantity);

    @Update("UPDATE t_sku SET stock = stock + #{quantity} WHERE sku_no = #{skuNo} AND is_deleted = 0")
    int addStock(@Param("skuNo") String skuNo, @Param("quantity") int quantity);
}
