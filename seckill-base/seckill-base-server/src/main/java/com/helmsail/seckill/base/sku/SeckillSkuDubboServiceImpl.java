package com.helmsail.seckill.base.sku;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 秒杀 SKU Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class SeckillSkuDubboServiceImpl implements SeckillSkuService {

    private final SeckillSkuBizService seckillSkuBizService;

    @Override
    public void addSku(AddSkuRequest request) {
        seckillSkuBizService.addSku(request);
    }

    @Override
    public RemoveSkuResponse removeSku(RemoveSkuRequest request) {
        return seckillSkuBizService.removeSku(request);
    }

    @Override
    public List<SeckillSkuDTO> listBySkProductId(String skProductId) {
        return seckillSkuBizService.listBySkProductId(skProductId);
    }
}
