package com.helmsail.seckill.support.server.product;

import com.helmsail.seckill.common.result.PageResult;
import com.helmsail.seckill.support.api.product.ProductService;
import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;

/**
 * 商品 Dubbo 服务实现
 */
@DubboService
@RequiredArgsConstructor
public class ProductDubboServiceImpl implements ProductService {

    private final ProductBizService productService;

    @Override
    public ProductDTO getBySpuNo(String spuNo) {
        return productService.getBySpuNo(spuNo);
    }

    @Override
    public PageResult<ProductDTO> page(ProductPageQuery query) {
        return productService.page(query);
    }
}
