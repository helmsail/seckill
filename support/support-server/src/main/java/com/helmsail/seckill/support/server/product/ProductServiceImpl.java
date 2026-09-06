package com.helmsail.seckill.support.server.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;
import com.helmsail.seckill.support.api.product.ProductPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 商品服务实现
 */
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductBizService {

    private final ProductMapper productMapper;

    @Override
    public ProductDTO getBySpuNo(String spuNo) {
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getSpuNo, spuNo));
        if (product == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setSpuNo(product.getSpuNo());
        dto.setProductName(product.getProductName());
        return dto;
    }

    @Override
    public ProductPageResult page(ProductPageQuery query) {
        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());
        productMapper.selectPage(page, null);
        java.util.List<ProductDTO> list = page.getRecords().stream()
                .map(p -> {
                    ProductDTO dto = new ProductDTO();
                    dto.setId(p.getId());
                    dto.setSpuNo(p.getSpuNo());
                    dto.setProductName(p.getProductName());
                    return dto;
                })
                .toList();
        return new ProductPageResult(list, page.getTotal(), page.getCurrent(), page.getSize());
    }
}
