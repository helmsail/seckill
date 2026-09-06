package com.helmsail.seckill.support.server.product;

import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;
import com.helmsail.seckill.support.api.product.ProductPageResult;

/**
 * 商品内部服务接口
 */
public interface ProductBizService {

    ProductDTO getBySpuNo(String spuNo);

    ProductPageResult page(ProductPageQuery query);
}
