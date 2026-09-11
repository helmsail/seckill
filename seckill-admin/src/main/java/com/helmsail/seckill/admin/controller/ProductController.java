package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.base.product.*;
import com.helmsail.seckill.common.result.PageResult;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.product.ProductDTO;
import com.helmsail.seckill.support.api.product.ProductPageQuery;
import com.helmsail.seckill.support.api.product.ProductService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 商品管理 Controller
 */
@RestController
@RequestMapping("/product")
@RequiredArgsConstructor
public class ProductController {

    @DubboReference
    private ProductService productService;

    @DubboReference
    private SeckillProductService seckillProductService;

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
     * 给活动添加商品
     */
    @PostMapping("/activity")
    public Result<Void> addToActivity(@RequestBody AddProductRequest request) {
        seckillProductService.addToActivity(request);
        return Result.success();
    }

    /**
     * 给活动移除商品
     */
    @DeleteMapping("/activity")
    public Result<Void> removeFromActivity(@RequestParam String activityNo, @RequestParam String spuNo) {
        seckillProductService.removeFromActivity(activityNo, spuNo);
        return Result.success();
    }

    /**
     * 查询活动下的秒杀商品列表
     */
    @GetMapping("/activity/{activityNo}")
    public Result<List<SeckillProductDTO>> listByActivityNo(@PathVariable String activityNo) {
        return Result.success(seckillProductService.listByActivityNo(activityNo));
    }
}
