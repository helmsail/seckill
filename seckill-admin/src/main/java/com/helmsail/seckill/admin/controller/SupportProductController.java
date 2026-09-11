package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.common.result.PageResult;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;
import com.helmsail.seckill.support.api.product.ProductService;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

/**
 * 主域商品 Controller（支撑域只读窗口）
 *
 * 为管理端配置活动提供选品数据源：SPU/SKU 均为主域（support）数据，只读转发。
 */
@RestController
@RequestMapping("/support/product")
@RequiredArgsConstructor
public class SupportProductController {

    @DubboReference
    private ProductService productService;

    @DubboReference
    private SkuService skuService;

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

    /**
     * 查询单个主域 SKU
     */
    @GetMapping("/sku/{skuNo}")
    public Result<SkuDTO> getBySkuNo(@PathVariable String skuNo) {
        return Result.success(skuService.getBySkuNo(skuNo));
    }
}
