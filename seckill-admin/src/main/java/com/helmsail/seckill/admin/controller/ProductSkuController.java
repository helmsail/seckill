package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.base.productsku.AddProductSkuRequest;
import com.helmsail.seckill.base.productsku.RemoveProductSkuRequest;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.productsku.ShelfProductSkuRequest;
import com.helmsail.seckill.base.productsku.StockRestoreItem;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.sku.SkuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 活动商品SKU Controller（与 seckill-base 的 productsku 领域对齐）
 *
 * 活动商品配置的编排入口：主域库存划拨/归还由本层编排（先划拨后落库，失败补偿）。
 */
@Slf4j
@RestController
@RequestMapping("/product-sku")
@RequiredArgsConstructor
public class ProductSkuController {

    @DubboReference
    private SkuService skuService;

    @DubboReference
    private SeckillProductSkuService seckillProductSkuService;

    /**
     * 批量添加到活动
     *
     * 编排：逐条划拨主域库存 → 秒杀域批量落库；任一步失败补偿已划拨部分
     *
     * TODO 补偿盲区：deductStock 若"远端已执行但响应失败（超时/断连）"，本地 deducted 计数漏记该条，
     * 补偿循环不包含它 → 主域库存被扣但活动商品未落库（库存黑洞，需人工找回）。
     * 失败方向偏向"少卖"不超卖，顺序正确；改进路径（由轻到重）：
     *   1. 失败日志输出已划拨清单（已完成）
     *   2. deductStock 幂等化（调用方传 requestId，服务端去重）+ 失败时查询确认实际扣减状态再补偿
     *   3. 对账任务（定时比对主域库存扣减与秒杀域落库记录，自动/半自动修复黑洞）
     */
    @PostMapping
    public Result<Void> batchAdd(@Valid @RequestBody AddProductSkuRequest request) {
        List<AddProductSkuRequest.Item> items = request.getItems();
        int deducted = 0;
        try {
            for (AddProductSkuRequest.Item item : items) {
                // TODO 若远端扣减成功但响应失败（假失败），deducted 不会自增 → 补偿漏账（库存黑洞），见方法注释
                skuService.deductStock(item.getSkuNo(), item.getActivityStock());
                deducted++;
            }
            seckillProductSkuService.batchAdd(request);
        } catch (Exception e) {
            String deductedSkus = items.subList(0, deducted).stream()
                    .map(item -> item.getSkuNo() + ":" + item.getActivityStock())
                    .collect(Collectors.joining(", "));
            log.error("添加活动商品失败，开始补偿归还: activityNo={}, 已划拨 {} 条=[{}]",
                    request.getActivityNo(), deducted, deductedSkus, e);
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
    @DeleteMapping
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
    @PutMapping("/shelf")
    public Result<Void> batchShelf(@Valid @RequestBody ShelfProductSkuRequest request) {
        seckillProductSkuService.batchShelf(request);
        return Result.success();
    }

    /**
     * 查询活动下的商品SKU列表
     */
    @GetMapping("/list")
    public Result<List<SeckillProductSkuDTO>> listByActivityNo(@RequestParam String activityNo) {
        return Result.success(seckillProductSkuService.listByActivityNo(activityNo));
    }
}
