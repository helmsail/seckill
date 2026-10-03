package com.helmsail.seckill.base.productsku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.helmsail.seckill.base.activity.Activity;
import com.helmsail.seckill.base.activity.ActivityMapper;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.productsku.AddSeckillProductSkuRequest.SkuConfig;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动商品SKU服务实现（Dubbo 暴露）
 *
 * 状态规则收敛在此：添加/删除仅待开始；上架/下架非终态可用。
 * 本服务含非幂等写操作（批量增删/上下架），消费方须禁用自动重试
 * （Dubbo 重试由 Consumer 决定，Provider 侧 retries 不生效）。
 */
@Service
@DubboService
@RequiredArgsConstructor
public class SeckillProductSkuDubboServiceImpl implements SeckillProductSkuDubboService {

    private static final int SHELF_ON = 1;
    private static final int SHELF_OFF = 0;

    private final SeckillProductSkuMapper seckillProductSkuMapper;
    private final ActivityMapper activityMapper;

    // ========== 写操作 ==========

    @Override
    @Transactional
    public void batchAdd(AddSeckillProductSkuRequest request) {
        String activityNo = request.getActivityNo();
        checkActivityStatus(activityNo, ActivityStatus.PENDING, "仅待开始状态可添加商品");
        List<SkuConfig> items = requireNonEmpty(request.getItems(), "添加列表不能为空");
        checkNoDuplicateSkuNos(items.stream().map(SkuConfig::getSkuNo).toList(), "添加列表存在重复 SKU");
        for (SkuConfig item : items) {
            validateItem(item);
            checkNotInActivity(activityNo, item.getSkuNo());
            seckillProductSkuMapper.insert(toEntity(activityNo, item));
        }
    }

    @Override
    @Transactional
    public List<StockRestoreItem> batchRemove(RemoveSeckillProductSkuRequest request) {
        String activityNo = request.getActivityNo();
        checkActivityStatus(activityNo, ActivityStatus.PENDING, "仅待开始状态可删除商品");
        List<String> skuNos = requireNonEmpty(request.getSkuNos(), "删除列表不能为空");
        checkNoDuplicateSkuNos(skuNos, "删除列表存在重复 SKU");
        List<SeckillProductSku> rows = seckillProductSkuMapper.selectList(new LambdaQueryWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, activityNo)
                .in(SeckillProductSku::getSkuNo, skuNos));
        if (rows.size() != skuNos.size()) {
            throw new BizException(SeckillResultEnum.SKU_NOT_FOUND);
        }
        int deleted = seckillProductSkuMapper.delete(new LambdaQueryWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, activityNo)
                .in(SeckillProductSku::getSkuNo, skuNos));
        if (deleted != rows.size()) {
            // 并发中行已消失：整批回滚，避免返回已失效的归还清单
            throw new BizException(SeckillResultEnum.SKU_NOT_FOUND.getCode(), "部分 SKU 已不存在，请刷新后重试");
        }
        // 归还清单：数量以库内快照为准（调用方据此编排归还主域）
        return rows.stream()
                .map(row -> new StockRestoreItem(row.getSkuNo(), row.getActivityStock()))
                .toList();
    }

    @Override
    @Transactional
    public void batchShelf(ShelfSeckillProductSkuRequest request) {
        String activityNo = request.getActivityNo();
        checkActivityNotClosed(activityNo);
        List<String> skuNos = requireNonEmpty(request.getSkuNos(), "操作列表不能为空");
        checkNoDuplicateSkuNos(skuNos, "操作列表存在重复 SKU");
        List<SeckillProductSku> existing = seckillProductSkuMapper.selectList(new LambdaQueryWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, activityNo)
                .in(SeckillProductSku::getSkuNo, skuNos));
        Set<String> existingSkuNos = existing.stream().map(SeckillProductSku::getSkuNo).collect(Collectors.toSet());
        for (String skuNo : skuNos) {
            if (!existingSkuNos.contains(skuNo)) {
                throw new BizException(SeckillResultEnum.SKU_NOT_FOUND.getCode(), "SKU 不存在: " + skuNo);
            }
        }
        int updated = seckillProductSkuMapper.update(null, new LambdaUpdateWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, activityNo)
                .in(SeckillProductSku::getSkuNo, skuNos)
                .set(SeckillProductSku::getShelfStatus, request.isOnShelf() ? SHELF_ON : SHELF_OFF));
        if (updated != skuNos.size()) {
            // 并发中行已消失：整批回滚（UPDATE 返回匹配行数，重复设置同值不会误报）
            throw new BizException(SeckillResultEnum.SKU_NOT_FOUND.getCode(), "部分 SKU 已不存在，请刷新后重试");
        }
    }

    // ========== 查询 ==========

    @Override
    public List<SeckillProductSkuDTO> listByActivityNo(String activityNo) {
        List<SeckillProductSku> list = seckillProductSkuMapper.selectList(
                new LambdaQueryWrapper<SeckillProductSku>()
                        .eq(SeckillProductSku::getActivityNo, activityNo)
                        .orderByAsc(SeckillProductSku::getId));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public SeckillProductSkuDTO getByActivityNoAndSkuNo(String activityNo, String skuNo) {
        SeckillProductSku row = seckillProductSkuMapper.selectOne(
                new LambdaQueryWrapper<SeckillProductSku>()
                        .eq(SeckillProductSku::getActivityNo, activityNo)
                        .eq(SeckillProductSku::getSkuNo, skuNo));
        return row == null ? null : toDTO(row);
    }

    // ========== 辅助方法 ==========

    private void checkActivityStatus(String activityNo, ActivityStatus required, String message) {
        Activity activity = getActivityForUpdate(activityNo);
        if (ActivityStatus.byCode(activity.getActivityStatus()) != required) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), message);
        }
    }

    /**
     * 锁定读活动行：与活动删除等状态变更串行化（调用方须已在事务内）
     */
    private Activity getActivityForUpdate(String activityNo) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo).last("FOR UPDATE"));
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private void checkActivityNotClosed(String activityNo) {
        Activity activity = getActivity(activityNo);
        if (ActivityStatus.byCode(activity.getActivityStatus()) == ActivityStatus.CLOSED) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "活动已关闭，不可操作商品");
        }
    }

    /**
     * 查询活动（不存在抛异常）
     */
    private Activity getActivity(String activityNo) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    /**
     * 批量列表非空断言
     */
    private static <T> List<T> requireNonEmpty(List<T> list, String message) {
        if (list == null || list.isEmpty()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), message);
        }
        return list;
    }

    /**
     * 批内去重：重复 SKU 编号直接拒绝
     */
    private static void checkNoDuplicateSkuNos(List<String> skuNos, String message) {
        Set<String> seen = new HashSet<>();
        for (String skuNo : skuNos) {
            if (!seen.add(skuNo)) {
                throw new BizException(ResultEnum.PARAM_ERROR.getCode(), message + ": " + skuNo);
            }
        }
    }

    /**
     * 查重：SKU 已在活动中则拒绝
     */
    private void checkNotInActivity(String activityNo, String skuNo) {
        Long exists = seckillProductSkuMapper.selectCount(new LambdaQueryWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, activityNo)
                .eq(SeckillProductSku::getSkuNo, skuNo));
        if (exists != null && exists > 0) {
            throw new BizException(SeckillResultEnum.SKU_ALREADY_EXISTS.getCode(), "SKU 已在活动中: " + skuNo);
        }
    }

    private void validateItem(SkuConfig item) {
        if (item.getSpuNo() == null || item.getSpuName() == null
                || item.getSkuNo() == null || item.getSkuName() == null
                || item.getOriginalPrice() == null || item.getDiscountType() == null
                || item.getDiscountParameter() == null || item.getActivityStock() == null) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "SKU 配置不完整: " + item.getSkuNo());
        }
        if (item.getActivityStock() < 1) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "秒杀库存必须大于 0: " + item.getSkuNo());
        }
        if (item.getPurchaseLimit() != null && item.getPurchaseLimit() < 0) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "限购不能为负数: " + item.getSkuNo());
        }
        BigDecimal seckillPrice = calculateSeckillPrice(item);
        if (seckillPrice.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "秒杀价必须大于 0: " + item.getSkuNo());
        }
        if (seckillPrice.compareTo(item.getOriginalPrice()) > 0) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "秒杀价不得高于原价: " + item.getSkuNo());
        }
    }

    private BigDecimal calculateSeckillPrice(SkuConfig item) {
        DiscountType type = item.getDiscountType();
        BigDecimal originalPrice = item.getOriginalPrice();
        BigDecimal parameter = item.getDiscountParameter();
        return switch (type) {
            case FIXED_PRICE -> parameter;
            case DISCOUNT -> originalPrice.multiply(parameter);
            case FIXED_REDUCTION -> originalPrice.subtract(parameter);
        };
    }

    private SeckillProductSku toEntity(String activityNo, SkuConfig item) {
        SeckillProductSku row = new SeckillProductSku();
        row.setActivityNo(activityNo);
        row.setSpuNo(item.getSpuNo());
        row.setSpuName(item.getSpuName());
        row.setSkuNo(item.getSkuNo());
        row.setSkuName(item.getSkuName());
        row.setDiscountType(item.getDiscountType().getCode());
        row.setDiscountParameter(item.getDiscountParameter());
        row.setOriginalPrice(item.getOriginalPrice());
        row.setSeckillPrice(calculateSeckillPrice(item));
        row.setActivityStock(item.getActivityStock());
        row.setPurchaseLimit(item.getPurchaseLimit() == null ? 0 : item.getPurchaseLimit());
        row.setShelfStatus(SHELF_ON);
        return row;
    }

    private SeckillProductSkuDTO toDTO(SeckillProductSku row) {
        SeckillProductSkuDTO dto = new SeckillProductSkuDTO();
        dto.setId(row.getId());
        dto.setActivityNo(row.getActivityNo());
        dto.setSpuNo(row.getSpuNo());
        dto.setSpuName(row.getSpuName());
        dto.setSkuNo(row.getSkuNo());
        dto.setSkuName(row.getSkuName());
        dto.setDiscountType(DiscountType.byCode(row.getDiscountType()));
        dto.setDiscountParameter(row.getDiscountParameter());
        dto.setOriginalPrice(row.getOriginalPrice());
        dto.setSeckillPrice(row.getSeckillPrice());
        dto.setActivityStock(row.getActivityStock());
        dto.setPurchaseLimit(row.getPurchaseLimit());
        dto.setShelfStatus(row.getShelfStatus());
        return dto;
    }
}
