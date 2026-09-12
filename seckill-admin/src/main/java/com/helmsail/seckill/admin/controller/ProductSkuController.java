package com.helmsail.seckill.admin.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helmsail.seckill.base.productsku.AddProductSkuRequest;
import com.helmsail.seckill.base.productsku.RemoveProductSkuRequest;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuService;
import com.helmsail.seckill.base.productsku.ShelfProductSkuRequest;
import com.helmsail.seckill.base.productsku.StockRestoreItem;
import com.helmsail.seckill.base.redis.SeckillRedisKey;
import com.helmsail.seckill.common.redis.RedisService;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.support.api.sku.SkuService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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

    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    /**
     * 批量添加到活动
     *
     * 编排：逐条划拨主域库存 → 秒杀域批量落库；任一步失败补偿已划拨部分。
     *
     * 幂等保障：划拨/归还均携带 batchId 派生的 requestId（服务端流水去重）；
     * 故障条失败后同 ID 重放确认——首次实际已扣则命中幂等返回并纳入补偿，
     * 库存黑洞收敛为“重放确认也失败”的极端残留（有 warn 日志可定位）。
     * 补偿归还失败登记 seckill:compensation:pending，由 compensationJob 按同 ID 重试
     * （上限 3 次，超限转 seckill:compensation:failed 人工处理）。
     */
    @PostMapping
    public Result<Void> batchAdd(@Valid @RequestBody AddProductSkuRequest request) {
        List<AddProductSkuRequest.Item> items = request.getItems();
        String batchId = newBatchId();
        List<AddProductSkuRequest.Item> deductedItems = new ArrayList<>(items.size());
        try {
            for (AddProductSkuRequest.Item item : items) {
                skuService.deductStock(item.getSkuNo(), item.getActivityStock(),
                        deductRequestId(batchId, item.getSkuNo()));
                deductedItems.add(item);
            }
            seckillProductSkuService.batchAdd(request);
        } catch (Exception e) {
            // 故障条重放确认（requestId 幂等）：首次 deduct 可能“实际成功但响应丢失”，
            // 同 ID 重放命中幂等返回即确认已扣，纳入补偿归还，消除库存黑洞
            if (deductedItems.size() < items.size()) {
                AddProductSkuRequest.Item suspect = items.get(deductedItems.size());
                try {
                    skuService.deductStock(suspect.getSkuNo(), suspect.getActivityStock(),
                            deductRequestId(batchId, suspect.getSkuNo()));
                    deductedItems.add(suspect);
                    log.warn("故障条重放确认已扣减，纳入补偿: skuNo={}", suspect.getSkuNo());
                } catch (Exception confirmEx) {
                    log.warn("故障条重放确认失败，保持不回补（可能未扣减）: skuNo={}, error={}",
                            suspect.getSkuNo(), confirmEx.getMessage());
                }
            }
            String deductedSkus = deductedItems.stream()
                    .map(item -> item.getSkuNo() + ":" + item.getActivityStock())
                    .collect(Collectors.joining(", "));
            log.error("添加活动商品失败，开始补偿归还: activityNo={}, 已划拨 {} 条=[{}]",
                    request.getActivityNo(), deductedItems.size(), deductedSkus, e);
            for (AddProductSkuRequest.Item item : deductedItems) {
                String restoreId = restoreRequestId(batchId, item.getSkuNo());
                try {
                    skuService.addStock(item.getSkuNo(), item.getActivityStock(), restoreId);
                } catch (Exception ex) {
                    log.error("补偿归还库存失败，已登记待补偿: skuNo={}", item.getSkuNo(), ex);
                    recordCompensation("ADD_ROLLBACK", request.getActivityNo(),
                            item.getSkuNo(), item.getActivityStock(), restoreId, ex);
                }
            }
            throw e;
        }
        return Result.success();
    }

    /**
     * 批量删除（物理删除，仅待开始状态）
     *
     * 编排：秒杀域删除并取回需归还清单 → 归还主域（失败登记补偿任务，由 compensationJob 重试归还）
     */
    @DeleteMapping
    public Result<Void> batchRemove(@Valid @RequestBody RemoveProductSkuRequest request) {
        List<StockRestoreItem> restoreItems = seckillProductSkuService.batchRemove(request);
        String batchId = newBatchId();
        for (StockRestoreItem item : restoreItems) {
            String restoreId = restoreRequestId(batchId, item.getSkuNo());
            try {
                skuService.addStock(item.getSkuNo(), item.getStockToRestore(), restoreId);
            } catch (Exception e) {
                log.error("归还主域库存失败，已登记待补偿: skuNo={}, stock={}",
                        item.getSkuNo(), item.getStockToRestore(), e);
                recordCompensation("REMOVE_RESTORE", request.getActivityNo(),
                        item.getSkuNo(), item.getStockToRestore(), restoreId, e);
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

    /**
     * 登记库存归还补偿（Redis Hash）：归还失败时兜底，由 compensationJob 定时重试，
     * 重试超限转入 seckill:compensation:failed 供人工处理。
     * requestId 随记录保存——补偿重试使用同一 ID，服务端幂等去重（重复执行无副作用）
     */
    private void recordCompensation(String type, String activityNo, String skuNo, int stock,
                                    String requestId, Exception cause) {
        try {
            Map<String, Object> record = new LinkedHashMap<>();
            record.put("type", type);
            record.put("activityNo", activityNo);
            record.put("skuNo", skuNo);
            record.put("stock", stock);
            record.put("requestId", requestId);
            record.put("retryCount", 0);
            record.put("createTime", LocalDateTime.now().toString());
            record.put("lastError", cause.getClass().getSimpleName() + ": " + cause.getMessage());
            String field = type + ":" + activityNo + ":" + skuNo;
            redisService.hSet(SeckillRedisKey.KEY_COMPENSATION_PENDING, field,
                    objectMapper.writeValueAsString(record));
        } catch (Exception e) {
            log.error("补偿登记失败（Redis 不可用），需人工核对: type={}, activityNo={}, skuNo={}, stock={}",
                    type, activityNo, skuNo, stock, e);
        }
    }

    /** 批次 ID：16 位随机串，作为本批划拨/归还 requestId 的稳定前缀（跨重试/重放不变） */
    private static String newBatchId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private static String deductRequestId(String batchId, String skuNo) {
        return batchId + ":" + skuNo + ":D";
    }

    private static String restoreRequestId(String batchId, String skuNo) {
        return batchId + ":" + skuNo + ":R";
    }
}
