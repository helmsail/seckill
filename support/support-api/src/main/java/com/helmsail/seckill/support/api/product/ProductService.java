package com.helmsail.seckill.support.api.product;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.PageResult;

/**
 * 商品 Dubbo 服务接口
 */
public interface ProductService {

    /**
     * 根据商品编号查询
     */
    ProductDTO getBySpuNo(String spuNo) throws BizException;

    /**
     * 分页查询商品
     */
    PageResult<ProductDTO> page(ProductPageQuery query) throws BizException;
}
