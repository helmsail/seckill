package com.helmsail.seckill.support.server.product;

import com.helmsail.seckill.common.result.PageResult;
import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;

/**
 * 商品内部服务接口
 */
public interface ProductBizService {

    ProductDTO getBySpuNo(String spuNo);

    PageResult<ProductDTO> page(ProductPageQuery query);
}
