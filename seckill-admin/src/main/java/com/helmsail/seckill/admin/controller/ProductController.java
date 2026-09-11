package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.common.result.PageResult;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;
import com.helmsail.seckill.support.api.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 商品管理 Controller（主域商品浏览）
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    @DubboReference
    private ProductService productService;

    /**
     * 分页查询主域商品
     */
    @GetMapping("/list")
    public Result<PageResult<ProductDTO>> page(ProductPageQuery query) {
        return Result.success(productService.page(query));
    }

    /**
     * 查询单个主域商品
     */
    @GetMapping("/{spuNo}")
    public Result<ProductDTO> getBySpuNo(@PathVariable String spuNo) {
        return Result.success(productService.getBySpuNo(spuNo));
    }
}
