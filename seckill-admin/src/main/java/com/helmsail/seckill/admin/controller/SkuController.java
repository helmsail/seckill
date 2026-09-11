package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.base.productsku.AddProductSkuRequest;
import com.helmsail.seckill.base.productsku.RemoveProductSkuRequest;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.productsku.ShelfProductSkuRequest;
import com.helmsail.seckill.base.productsku.StockRestoreItem;
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
 *
 * 活动商品SKU的编排入口：主域库存划拨/归还由本层编排（先划拨后落库，失败补偿）。
 */
@Slf4j
@RestController
@RequestMapping("/sku")
@RequiredArgsConstructor
public class SkuController {

    @DubboReference
    private SkuService skuService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    /**
     * 查询单个主域 SKU
     */
    @GetMapping("/{skuNo}")
    public Result<SkuDTO> getBySkuNo(@PathVariable String skuNo) {
        return Result.success(skuService.getBySkuNo(skuNo));
    }

    /**
     * 批量添加到活动
     *
     * 编排：逐条划拨主域库存 → 秒杀域批量落库；任一步失败补偿已划拨部分
     */
    @PostMapping("/seckill")
    public Result<Void> batchAdd(@Valid @RequestBody AddProductSkuRequest request) {
        List<AddProductSkuRequest.Item> items = request.getItems();
        int deducted = 0;
        try {
            for (AddProductSkuRequest.Item item : items) {
                skuService.deductStock(item.getSkuNo(), item.getActivityStock());
                deducted++;
            }
            seckillProductSkuService.batchAdd(request);
        } catch (Exception e) {
            log.error("添加活动商品失败，补偿归还库存: activityNo={}", request.getActivityNo(), e);
            for (int i = 0; i < deducted; i++) {
                AddProductSkuRequest.Item item = items.get(i);
                try {
                    skuService.addStock(item.getSkuNo(), item.getActivityStock());
                } catch (Exception ex) {
                    log.error("补偿归还库存失败: skuNo={}", item.getSkuNo(), ex);
                }
            }
            throw e;
        }
        return Result.success();
    }

    /**
     * 批量删除（物理删除，仅待开始状态）
     *
     * 编排：秒杀域删除并取回需归还清单 → 归还主域（失败记录待人工/对账兜底）
     */
    @DeleteMapping("/seckill")
    public Result<Void> batchRemove(@Valid @RequestBody RemoveProductSkuRequest request) {
        List<StockRestoreItem> restoreItems = seckillProductSkuService.batchRemove(request);
        for (StockRestoreItem item : restoreItems) {
            try {
                skuService.addStock(item.getSkuNo(), item.getStockToRestore());
            } catch (Exception e) {
                log.error("归还主域库存失败，需人工核对: skuNo={}, stock={}",
                        item.getSkuNo(), item.getStockToRestore(), e);
            }
        }
        return Result.success();
    }

    /**
     * 批量上架/下架（活动非终态可用）
     */
    @PutMapping("/seckill/shelf")
    public Result<Void> batchShelf(@Valid @RequestBody ShelfProductSkuRequest request) {
        seckillProductSkuService.batchShelf(request);
        return Result.success();
    }

    /**
     * 查询活动下的商品SKU列表
     */
    @GetMapping("/seckill")
    public Result<List<SeckillProductSkuDTO>> listByActivityNo(@RequestParam String activityNo) {
        return Result.success(seckillProductSkuService.listByActivityNo(activityNo));
    }
}
