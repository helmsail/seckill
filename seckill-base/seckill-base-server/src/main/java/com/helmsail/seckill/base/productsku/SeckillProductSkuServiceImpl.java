package com.helmsail.seckill.base.productsku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.helmsail.seckill.base.activity.Activity;
import com.helmsail.seckill.base.activity.ActivityMapper;
import com.helmsail.seckill.base.activity.ActivityStatus;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 活动商品SKU服务实现
 *
 * 状态规则收敛在此：添加/删除仅待开始；上架/下架非终态可用。
 */
@Service
@RequiredArgsConstructor
public class SeckillProductSkuServiceImpl implements SeckillProductSkuBizService {

    private static final int SHELF_ON = 1;
    private static final int SHELF_OFF = 0;

    private final SeckillProductSkuMapper seckillProductSkuMapper;
    private final ActivityMapper activityMapper;

    @Override
    @Transactional
    public void batchAdd(AddProductSkuRequest request) {
        checkActivityStatus(request.getActivityNo(), ActivityStatus.PENDING, "仅待开始状态可添加商品");
        List<AddProductSkuRequest.Item> items = request.getItems();
        if (items == null || items.isEmpty()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "添加列表不能为空");
        }
        Set<String> skuNos = new HashSet<>();
        for (AddProductSkuRequest.Item item : items) {
            validateItem(item);
            if (!skuNos.add(item.getSkuNo())) {
                throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "添加列表存在重复 SKU: " + item.getSkuNo());
            }
            Long exists = seckillProductSkuMapper.selectCount(new LambdaQueryWrapper<SeckillProductSku>()
                    .eq(SeckillProductSku::getActivityNo, request.getActivityNo())
                    .eq(SeckillProductSku::getSkuNo, item.getSkuNo()));
            if (exists != null && exists > 0) {
                throw new BizException(SeckillResultEnum.SKU_ALREADY_EXISTS.getCode(), "SKU 已在活动中: " + item.getSkuNo());
            }
            seckillProductSkuMapper.insert(toEntity(request.getActivityNo(), item));
        }
    }

    @Override
    @Transactional
    public List<StockRestoreItem> batchRemove(RemoveProductSkuRequest request) {
        checkActivityStatus(request.getActivityNo(), ActivityStatus.PENDING, "仅待开始状态可删除商品");
        List<String> skuNos = request.getSkuNos();
        if (skuNos == null || skuNos.isEmpty()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "删除列表不能为空");
        }
        List<SeckillProductSku> rows = seckillProductSkuMapper.selectList(new LambdaQueryWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, request.getActivityNo())
                .in(SeckillProductSku::getSkuNo, skuNos));
        if (rows.size() != new HashSet<>(skuNos).size()) {
            throw new BizException(SeckillResultEnum.SKU_NOT_FOUND);
        }
        List<StockRestoreItem> restoreItems = new ArrayList<>();
        for (SeckillProductSku row : rows) {
            restoreItems.add(new StockRestoreItem(row.getSkuNo(), row.getActivityStock()));
        }
        seckillProductSkuMapper.delete(new LambdaQueryWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, request.getActivityNo())
                .in(SeckillProductSku::getSkuNo, skuNos));
        return restoreItems;
    }

    @Override
    public void batchShelf(ShelfProductSkuRequest request) {
        checkActivityNotClosed(request.getActivityNo());
        List<String> skuNos = request.getSkuNos();
        if (skuNos == null || skuNos.isEmpty()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "操作列表不能为空");
        }
        Set<String> distinctSkuNos = new HashSet<>(skuNos);
        List<SeckillProductSku> existing = seckillProductSkuMapper.selectList(new LambdaQueryWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, request.getActivityNo())
                .in(SeckillProductSku::getSkuNo, distinctSkuNos));
        Set<String> existingSkuNos = existing.stream().map(SeckillProductSku::getSkuNo).collect(Collectors.toSet());
        for (String skuNo : distinctSkuNos) {
            if (!existingSkuNos.contains(skuNo)) {
                throw new BizException(SeckillResultEnum.SKU_NOT_FOUND.getCode(), "SKU 不存在: " + skuNo);
            }
        }
        seckillProductSkuMapper.update(null, new LambdaUpdateWrapper<SeckillProductSku>()
                .eq(SeckillProductSku::getActivityNo, request.getActivityNo())
                .in(SeckillProductSku::getSkuNo, distinctSkuNos)
                .set(SeckillProductSku::getShelfStatus, request.isOnShelf() ? SHELF_ON : SHELF_OFF));
    }

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

    private void checkActivityStatus(String activityNo, ActivityStatus required, String message) {
        Activity activity = getActivity(activityNo);
        if (ActivityStatus.byCode(activity.getActivityStatus()) != required) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), message);
        }
    }

    private void checkActivityNotClosed(String activityNo) {
        Activity activity = getActivity(activityNo);
        if (ActivityStatus.byCode(activity.getActivityStatus()) == ActivityStatus.CLOSED) {
            throw new BizException(SeckillResultEnum.ACTIVITY_STATUS_ERROR.getCode(), "活动已关闭，不可操作商品");
        }
    }

    private Activity getActivity(String activityNo) {
        Activity activity = activityMapper.selectOne(
                new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, activityNo));
        if (activity == null) {
            throw new BizException(SeckillResultEnum.ACTIVITY_NOT_FOUND);
        }
        return activity;
    }

    private void validateItem(AddProductSkuRequest.Item item) {
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
        if (calculateSeckillPrice(item).compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "秒杀价必须大于 0: " + item.getSkuNo());
        }
    }

    private SeckillProductSku toEntity(String activityNo, AddProductSkuRequest.Item item) {
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

    private BigDecimal calculateSeckillPrice(AddProductSkuRequest.Item item) {
        DiscountType type = item.getDiscountType();
        BigDecimal originalPrice = item.getOriginalPrice();
        BigDecimal parameter = item.getDiscountParameter();
        return switch (type) {
            case FIXED_PRICE -> parameter;
            case DISCOUNT -> originalPrice.multiply(parameter);
            case FIXED_REDUCTION -> originalPrice.subtract(parameter);
        };
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
