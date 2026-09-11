package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.base.sku.*;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * SKU 管理 Controller
 */
@Slf4j
@RestController
@RequestMapping("/sku")
@RequiredArgsConstructor
public class SkuController {

    @DubboReference
    private SkuService skuService;

    @DubboReference
    private SeckillSkuService seckillSkuService;

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
    public Result<Void> addSeckillSku(@Valid @RequestBody AddSkuRequest request) {
        // 1. 从主域扣减库存
        skuService.deductStock(request.getSkuNo(), request.getActivityStock());
        // 2. 标记已扣减，添加到秒杀域
        request.setStockDeducted(true);
        try {
            seckillSkuService.addSku(request);
        } catch (Exception e) {
            // 补偿：恢复主域库存
            log.error("添加秒杀SKU失败，补偿恢复库存: skuNo={}", request.getSkuNo(), e);
            skuService.addStock(request.getSkuNo(), request.getActivityStock());
            throw e;
        }
        return Result.success();
    }

    @DeleteMapping("/seckill")
    public Result<Void> removeSeckillSku(@Valid @RequestBody RemoveSkuRequest request) {
        // 1. 从秒杀域删除 SKU
        RemoveSkuResponse response = seckillSkuService.removeSku(request);
        // 2. 将库存归还主域
        try {
            skuService.addStock(response.getSkuNo(), response.getStockToRestore());
        } catch (Exception e) {
            // 补偿：恢复秒杀域 SKU
            log.error("归还库存失败，补偿恢复秒杀SKU: skuNo={}", response.getSkuNo(), e);
            AddSkuRequest addRequest = new AddSkuRequest();
            addRequest.setSkProductId(request.getSkProductId());
            addRequest.setSkuNo(response.getSkuNo());
            addRequest.setActivityStock(response.getStockToRestore());
            addRequest.setStockDeducted(true);
            seckillSkuService.addSku(addRequest);
            throw e;
        }
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
