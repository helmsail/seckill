package com.helmsail.seckill.support.server.sku;

import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuPageQuery;
import com.helmsail.seckill.support.api.sku.SkuPageResult;

import java.util.List;

/**
 * SKU 内部服务接口
 */
public interface SkuBizService {

    SkuDTO getBySkuNo(String skuNo);

    List<SkuDTO> listBySpuNo(String spuNo);

    void deductStock(String skuNo, int quantity);

    void addStock(String skuNo, int quantity);

    SkuPageResult page(SkuPageQuery query);
}
