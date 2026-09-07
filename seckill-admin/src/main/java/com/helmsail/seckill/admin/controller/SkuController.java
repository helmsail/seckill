package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.base.sku.*;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuPageQuery;
import com.helmsail.seckill.support.api.sku.SkuPageResult;
import com.helmsail.seckill.support.api.sku.SkuService;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SKU 管理 Controller
 */
@RestController
@RequestMapping("/sku")
@RequiredArgsConstructor
public class SkuController {

    @DubboReference
    private SkuService skuService;

    @DubboReference
    private SeckillSkuService seckillSkuService;

    /**
     * 分页查询主域 SKU
     */
    @GetMapping("/list")
    public Result<SkuPageResult> page(SkuPageQuery query) {
        return Result.success(skuService.page(query));
    }

    /**
     * 查询单个主域 SKU
     */
    @GetMapping("/{skuNo}")
    public Result<SkuDTO> getBySkuNo(@PathVariable String skuNo) {
        return Result.success(skuService.getBySkuNo(skuNo));
    }

    /**
     * 给秒杀商品添加 SKU
     *
     * 内部自动：主域扣减库存 → 秒杀域添加 SKU
     */
    @PostMapping("/seckill")
    public Result<Void> addSeckillSku(@RequestBody AddSkuRequest request) {
        // 1. 从主域扣减库存
        skuService.deductStock(request.getSkuNo(), request.getActivityStock());
        // 2. 标记已扣减，添加到秒杀域
        request.setStockDeducted(true);
        seckillSkuService.addSku(request);
        return Result.success();
    }

    /**
     * 给秒杀商品删除 SKU
     *
     * 内部自动：秒杀域删除 SKU → 主域归还库存
     */
    @DeleteMapping("/seckill")
    public Result<Void> removeSeckillSku(@RequestBody RemoveSkuRequest request) {
        // 1. 从秒杀域删除 SKU
        RemoveSkuResponse response = seckillSkuService.removeSku(request);
        // 2. 将库存归还主域
        skuService.addStock(response.getSkuNo(), response.getStockToRestore());
        return Result.success();
    }

    /**
     * 查询秒杀域某个商品的 SKU 列表
     */
    @GetMapping("/seckill/{skProductId}")
    public Result<List<SeckillSkuDTO>> listSeckillSku(@PathVariable String skProductId) {
        return Result.success(seckillSkuService.listBySkProductId(skProductId));
    }
}
