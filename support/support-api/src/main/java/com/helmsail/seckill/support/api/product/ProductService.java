package com.helmsail.seckill.support.api.product;

/**
 * 商品 Dubbo 服务接口
 */
public interface ProductService {

    /**
     * 根据商品编号查询
     */
    ProductDTO getBySpuNo(String spuNo);

    /**
     * 分页查询商品
     */
    ProductPageResult page(ProductPageQuery query);
}
