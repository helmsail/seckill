package com.helmsail.seckill.base.productsku;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 活动商品SKU Dubbo 服务实现
 *
 * retries = 0：本服务含非幂等写操作（批量增删/上下架），自动重试会产生重复副作用
 */
@DubboService(retries = 0)
@RequiredArgsConstructor
public class SeckillProductSkuDubboServiceImpl implements SeckillProductSkuService {

    private final SeckillProductSkuBizService seckillProductSkuBizService;

    @Override
    public void batchAdd(AddProductSkuRequest request) {
        seckillProductSkuBizService.batchAdd(request);
    }

    @Override
    public List<StockRestoreItem> batchRemove(RemoveProductSkuRequest request) {
        return seckillProductSkuBizService.batchRemove(request);
    }

    @Override
    public void batchShelf(ShelfProductSkuRequest request) {
        seckillProductSkuBizService.batchShelf(request);
    }

    @Override
    public List<SeckillProductSkuDTO> listByActivityNo(String activityNo) {
        return seckillProductSkuBizService.listByActivityNo(activityNo);
    }

    @Override
    public SeckillProductSkuDTO getByActivityNoAndSkuNo(String activityNo, String skuNo) {
        return seckillProductSkuBizService.getByActivityNoAndSkuNo(activityNo, skuNo);
    }
}
