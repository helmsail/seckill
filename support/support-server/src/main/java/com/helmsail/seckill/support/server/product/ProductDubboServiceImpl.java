package com.helmsail.seckill.support.server.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.PageResult;
import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;
import com.helmsail.seckill.support.api.product.ProductDubboService;
import com.helmsail.seckill.support.api.result.SupportResultEnum;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 商品服务实现（Dubbo 暴露）
 */
@Service
@DubboService
@RequiredArgsConstructor
public class ProductDubboServiceImpl implements ProductDubboService {

    private final ProductMapper productMapper;

    @Override
    public ProductDTO getBySpuNo(String spuNo) {
        Product product = productMapper.selectOne(
                new LambdaQueryWrapper<Product>().eq(Product::getSpuNo, spuNo));
        if (product == null) {
            throw new BizException(SupportResultEnum.PRODUCT_NOT_FOUND);
        }
        return toDTO(product);
    }

    @Override
    public PageResult<ProductDTO> page(ProductPageQuery query) {
        Page<Product> page = new Page<>(query.getPageNum(), query.getPageSize());
        // 主键排序：唯一键保证翻页稳定（不重不漏）；雪花主键趋势递增 ≈ 创建时间序
        LambdaQueryWrapper<Product> wrapper = new LambdaQueryWrapper<Product>()
                .orderByDesc(Product::getId);
        productMapper.selectPage(page, wrapper);
        List<ProductDTO> list = page.getRecords().stream().map(this::toDTO).toList();
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    private ProductDTO toDTO(Product product) {
        ProductDTO dto = new ProductDTO();
        dto.setId(product.getId());
        dto.setSpuNo(product.getSpuNo());
        dto.setProductName(product.getProductName());
        return dto;
    }
}
