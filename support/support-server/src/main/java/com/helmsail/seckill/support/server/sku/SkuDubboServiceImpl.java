package com.helmsail.seckill.support.server.sku;

import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuService;
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
    public List<SkuDTO> listBySkuName(String skuName) {
        return skuBizService.listBySkuName(skuName);
    }

    @Override
    public void deductStock(String skuNo, int quantity) {
        skuBizService.deductStock(skuNo, quantity);
    }

    @Override
    public void addStock(String skuNo, int quantity) {
        skuBizService.addStock(skuNo, quantity);
    }
}
