package com.helmsail.seckill.support.server.sku;

import com.helmsail.seckill.support.api.sku.SkuService;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuPageQuery;
import com.helmsail.seckill.support.api.sku.SkuPageResult;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * SKU Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class SkuDubboServiceImpl implements SkuService {

    private final SkuBizService skuBizService;

    @Override
    public SkuDTO getBySkuNo(String skuNo) {
        return skuBizService.getBySkuNo(skuNo);
    }

    @Override
    public List<SkuDTO> listBySpuNo(String spuNo) {
        return skuBizService.listBySpuNo(spuNo);
    }

    @Override
    public void deductStock(String skuNo, int quantity) {
        skuBizService.deductStock(skuNo, quantity);
    }

    @Override
    public SkuPageResult page(SkuPageQuery query) {
        return skuBizService.page(query);
    }
}
