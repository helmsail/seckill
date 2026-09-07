package com.helmsail.seckill.base.product;

import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

import java.util.List;

/**
 * 秒杀商品 Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class SeckillProductDubboServiceImpl implements SeckillProductService {

    private final SeckillProductBizService seckillProductBizService;

    @Override
    public void addToActivity(AddProductRequest request) {
        seckillProductBizService.addToActivity(request);
    }

    @Override
    public void removeFromActivity(String activityNo, String spuNo) {
        seckillProductBizService.removeFromActivity(activityNo, spuNo);
    }

    @Override
    public List<SeckillProductDTO> listByActivityNo(String activityNo) {
        return seckillProductBizService.listByActivityNo(activityNo);
    }
}
