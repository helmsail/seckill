package com.helmsail.seckill.admin.controller;

import com.helmsail.seckill.base.productsku.AddSeckillProductSkuRequest;
import com.helmsail.seckill.base.productsku.RemoveSeckillProductSkuRequest;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDTO;
import com.helmsail.seckill.base.productsku.SeckillProductSkuDubboService;
import com.helmsail.seckill.base.productsku.ShelfSeckillProductSkuRequest;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.Result;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.sku.SkuDubboService;
import com.helmsail.seckill.support.api.sku.StockChangeItem;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Method;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动商品SKU Controller（与 seckill-base 的 productsku 领域对齐）
 *
 * 跨域写编排：主域库存（support）与秒杀域配置（base）各以"域内单事务"保证批次全成全败；
 * 编排层只处理两段事务之间的失败裁决：
 *   - 确认失败（异常链含 BizException，事务已回滚=零副作用）→ 定向补偿/归还，可安全重试；
 *   - 未知结果（超时等，事务结果不可知）→ 不动作、记日志、转人工核对。
 */
@Slf4j
@RestController
@RequestMapping("/product-sku")
@RequiredArgsConstructor
public class ProductSkuController {

    /** 归还重试上限：仅"确认失败"可重试（事务回滚=零副作用）；超时不可重试 */
    private static final int RESTORE_MAX_ATTEMPTS = 3;

    /** 归还重试间隔（毫秒） */
    private static final long RESTORE_RETRY_INTERVAL_MS = 200L;

    /** 写：批量扣减/归还非幂等，方法级禁重试；读方法走引用默认 */
    @DubboReference(methods = {
            @Method(name = "batchDeductStock", retries = 0),
            @Method(name = "batchAddStock", retries = 0)
    })
    private SkuDubboService skuService;

    /** 读写混合：写方法方法级禁重试；读方法不声明，自动走引用级默认 */
    @DubboReference(methods = {
            @Method(name = "batchAdd", retries = 0),
            @Method(name = "batchRemove", retries = 0),
            @Method(name = "batchShelf", retries = 0)
    })
    private SeckillProductSkuDubboService seckillProductSkuService;

    /**
     * 批量添加到活动
     *
     * 编排：批量划拨主域库存（事务）→ 秒杀域批量落库（事务）；
     * 落库确认失败 → 整批补偿归还；任一阶段未知结果 → 不动作、转人工。
     */
    @PostMapping
    public Result<Void> batchAdd(@Valid @RequestBody AddSeckillProductSkuRequest request) {
        List<StockChangeItem> stockItems = toStockItemsForDeduct(request.getItems());

        // 阶段一：批量划拨（support 域内单事务：整批扣 / 零扣）
        try {
            skuService.batchDeductStock(stockItems);
        } catch (Exception e) {
            BizException biz = findBizException(e);
            if (biz != null) {
                // 确认失败：事务已回滚，零副作用，无需补偿
                throw biz;
            }
            // 未知结果：可能已划拨，不动作，转人工核对
            log.error("批量划拨库存结果未知，需人工审查: activityNo={}, items={}",
                    request.getActivityNo(), describe(stockItems), e);
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(),
                    "库存划拨结果未知，请人工核对主域库存后再决定是否重试");
        }

        // 阶段二：秒杀域批量落库（base 域内单事务：整批插 / 零插）
        try {
            seckillProductSkuService.batchAdd(request);
        } catch (Exception e) {
            BizException biz = findBizException(e);
            if (biz != null) {
                // 确认失败（零行落库，双前提成立）：整批补偿归还，随后上抛原始业务错误
                restoreStock(request.getActivityNo(), stockItems, "添加失败补偿归还");
                throw biz;
            }
            // 未知结果：可能已落库，不动作，转人工核对
            log.error("秒杀域落库结果未知，需人工审查: activityNo={}, items={}",
                    request.getActivityNo(), describe(stockItems), e);
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(),
                    "落库结果未知，请人工核对活动商品后再决定是否重试");
        }
        return Result.success();
    }

    /**
     * 批量删除（物理删除，仅待开始状态）
     *
     * 编排：预读配额快照 → 秒杀域批量删除（事务）→ 批量归还主域（事务）。
     * 确认失败 → 零删除直接报错；超时（未知）→ 查证行是否已删，
     * 已删则按预读快照继续归还，否则转人工。
     */
    @DeleteMapping
    public Result<Void> batchRemove(@Valid @RequestBody RemoveSeckillProductSkuRequest request) {
        String activityNo = request.getActivityNo();
        List<String> skuNos = request.getSkuNos();

        // 预读：删除成功后行即消失、配额不可再查，预读快照是超时后恢复归还的唯一依据
        List<StockChangeItem> preReadItems = preReadQuota(activityNo, skuNos);

        // 阶段一：秒杀域批量删除（base 域内单事务：整批删 / 零删）
        List<StockChangeItem> restoreItems;
        try {
            restoreItems = seckillProductSkuService.batchRemove(request).stream()
                    .map(item -> new StockChangeItem(item.getSkuNo(), item.getStockToRestore()))
                    .toList();
        } catch (Exception e) {
            BizException biz = findBizException(e);
            if (biz != null) {
                // 确认失败：事务已回滚，零删除，无需归还
                throw biz;
            }
            // 未知结果：查证行是否已删
            restoreItems = verifyRemoved(activityNo, skuNos, preReadItems);
            if (restoreItems == null) {
                log.error("批量删除结果未知且查证未通过，需人工审查: activityNo={}, skuNos={}",
                        activityNo, skuNos, e);
                throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(),
                        "删除结果未知，请人工核对活动商品后再决定是否重试");
            }
            log.warn("批量删除响应超时，查证确认已删除，按预读配额快照继续归还: activityNo={}, items={}",
                    activityNo, describe(restoreItems));
        }

        // 阶段二：批量归还主域（support 域内单事务：整批还 / 零还）
        restoreStock(activityNo, restoreItems, "删除归还");
        return Result.success();
    }

    /**
     * 批量上架/下架（活动非终态可用）
     */
    @PutMapping("/shelf")
    public Result<Void> batchShelf(@Valid @RequestBody ShelfSeckillProductSkuRequest request) {
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

    // ==================== 编排辅助 ====================

    /**
     * 添加请求 → 划拨清单（快速失败：空列表 / 缺 SKU 编号 / 库存非正，先于任何副作用）
     */
    private static List<StockChangeItem> toStockItemsForDeduct(List<AddSeckillProductSkuRequest.SkuConfig> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "添加列表不能为空");
        }
        List<StockChangeItem> stockItems = new ArrayList<>(items.size());
        for (AddSeckillProductSkuRequest.SkuConfig item : items) {
            if (item == null || !StringUtils.hasText(item.getSkuNo())) {
                throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "SKU 编号不能为空");
            }
            if (item.getActivityStock() == null || item.getActivityStock() <= 0) {
                throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "活动库存必须大于 0: " + item.getSkuNo());
            }
            stockItems.add(new StockChangeItem(item.getSkuNo(), item.getActivityStock()));
        }
        return stockItems;
    }

    /**
     * 预读待删 SKU 的配额快照；任一 SKU 不在活动中立即失败（与秒杀域删除的校验语义一致，零副作用）
     */
    private List<StockChangeItem> preReadQuota(String activityNo, List<String> skuNos) {
        if (skuNos == null || skuNos.isEmpty()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "删除列表不能为空");
        }
        List<SeckillProductSkuDTO> rows;
        try {
            rows = seckillProductSkuService.listByActivityNo(activityNo);
        } catch (Exception e) {
            BizException biz = findBizException(e);
            if (biz != null) {
                throw biz;
            }
            log.error("预读待删 SKU 配额失败: activityNo={}", activityNo, e);
            throw new BizException(ResultEnum.SYSTEM_ERROR.getCode(), "读取活动商品失败，请稍后重试");
        }
        Map<String, Integer> quotaMap = rows.stream().collect(Collectors.toMap(
                SeckillProductSkuDTO::getSkuNo, SeckillProductSkuDTO::getActivityStock, (a, b) -> a));
        List<StockChangeItem> items = new ArrayList<>(skuNos.size());
        for (String skuNo : skuNos) {
            Integer quota = quotaMap.get(skuNo);
            if (quota == null) {
                throw new BizException(SeckillResultEnum.SKU_NOT_FOUND.getCode(), "SKU 不在活动中: " + skuNo);
            }
            items.add(new StockChangeItem(skuNo, quota));
        }
        return items;
    }

    /**
     * 删除超时查证：重读活动 SKU 列表判定删除是否已生效。
     * 全部消失 = 已删除（返回预读快照清单）；仍有残留 / 读取失败 = null（转人工）
     */
    private List<StockChangeItem> verifyRemoved(String activityNo, List<String> skuNos, List<StockChangeItem> preReadItems) {
        List<SeckillProductSkuDTO> rows;
        try {
            rows = seckillProductSkuService.listByActivityNo(activityNo);
        } catch (Exception e) {
            log.error("删除超时后查证失败（读取活动商品异常）: activityNo={}, skuNos={}", activityNo, skuNos, e);
            return null;
        }
        Set<String> currentSkuNos = rows.stream()
                .map(SeckillProductSkuDTO::getSkuNo)
                .collect(Collectors.toSet());
        long remain = skuNos.stream().filter(currentSkuNos::contains).count();
        if (remain == 0) {
            return preReadItems;
        }
        log.error("删除超时后查证发现 SKU 仍在活动中（{}/{}），需人工核对: activityNo={}, skuNos={}",
                remain, skuNos.size(), activityNo, skuNos);
        return null;
    }

    /**
     * 批量归还主域库存（support 域内单事务：整批还 / 零还）。
     * 确认失败可安全重试（事务回滚=零副作用）；超时（未知结果）不重试，记日志转人工。
     */
    private void restoreStock(String activityNo, List<StockChangeItem> items, String scene) {
        for (int attempt = 1; attempt <= RESTORE_MAX_ATTEMPTS; attempt++) {
            try {
                skuService.batchAddStock(items);
                if (attempt > 1) {
                    log.info("归还主域库存重试成功（第 {} 次）: scene={}, activityNo={}, items={}",
                            attempt, scene, activityNo, describe(items));
                }
                return;
            } catch (Exception e) {
                boolean retryable = findBizException(e) != null;
                log.error("归还主域库存失败（第 {}/{} 次, scene={}, activityNo={}, items={}, 可重试={}）",
                        attempt, RESTORE_MAX_ATTEMPTS, scene, activityNo, describe(items), retryable, e);
                if (!retryable) {
                    // 超时（未知结果）：不重试，转人工
                    return;
                }
                if (attempt == RESTORE_MAX_ATTEMPTS) {
                    log.error("归还主域库存 {} 次尝试仍失败，需人工核对: scene={}, activityNo={}, items={}",
                            RESTORE_MAX_ATTEMPTS, scene, activityNo, describe(items));
                    return;
                }
                sleepQuietly(RESTORE_RETRY_INTERVAL_MS);
            }
        }
    }

    /**
     * 日志用条目描述
     */
    private static String describe(List<StockChangeItem> items) {
        if (items == null || items.isEmpty()) {
            return "[]";
        }
        return items.stream()
                .map(item -> item.getSkuNo() + ":" + item.getQuantity())
                .collect(Collectors.joining(", ", "[", "]"));
    }

    /**
     * 沿 cause 链查找 BizException（与 WebMvcExceptionHandler 的解包逻辑一致）：
     * 找到 = 跨进程业务异常（确认失败，事务已回滚）；未找到 = 超时/网络类（未知结果）。
     */
    private static BizException findBizException(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof BizException bizException) {
                return bizException;
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return null;
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
