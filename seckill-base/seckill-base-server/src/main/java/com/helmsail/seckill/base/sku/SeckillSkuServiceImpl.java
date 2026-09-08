package com.helmsail.seckill.base.sku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.base.activity.Activity;
import com.helmsail.seckill.base.activity.ActivityMapper;
import com.helmsail.seckill.base.product.DiscountType;
import com.helmsail.seckill.base.product.SeckillProduct;
import com.helmsail.seckill.base.product.SeckillProductMapper;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SeckillSkuServiceImpl implements SeckillSkuBizService {

    private final SeckillSkuMapper seckillSkuMapper;
    private final SeckillProductMapper seckillProductMapper;
    private final ActivityMapper activityMapper;

    @Override
    public void addSku(AddSkuRequest request) {
        if (!request.isStockDeducted()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "请先从主域扣减库存");
        }

        SeckillProduct product = seckillProductMapper.selectById(request.getSkProductId());
        if (product == null) {
            throw new BizException(ResultEnum.PRODUCT_NOT_FOUND);
        }
        if (request.getRequiredStatus() != null) {
            Activity activity = activityMapper.selectOne(
                    new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, product.getActivityNo()));
            if (activity == null || activity.getActivityStatus() != request.getRequiredStatus()) {
                throw new BizException(ResultEnum.ACTIVITY_STATUS_ERROR);
            }
        }

        BigDecimal seckillPrice = calculateSeckillPrice(
                request.getOriginalPrice(),
                product.getDiscountType(),
                product.getDiscountParameter());

        SeckillSku sku = new SeckillSku();
        sku.setSkProductId(request.getSkProductId());
        sku.setSkuNo(request.getSkuNo());
        sku.setSkuName(request.getSkuName());
        sku.setOriginalPrice(request.getOriginalPrice());
        sku.setSeckillPrice(seckillPrice);
        sku.setActivityStock(request.getActivityStock());
        sku.setPurchaseLimit(request.getPurchaseLimit());
        seckillSkuMapper.insert(sku);
    }

    @Override
    public RemoveSkuResponse removeSku(RemoveSkuRequest request) {
        SeckillSku sku = seckillSkuMapper.selectOne(
                new LambdaQueryWrapper<SeckillSku>()
                        .eq(SeckillSku::getSkProductId, request.getSkProductId())
                        .eq(SeckillSku::getSkuNo, request.getSkuNo()));
        if (sku == null) {
            throw new BizException(ResultEnum.SKU_NOT_FOUND);
        }

        SeckillProduct product = seckillProductMapper.selectById(request.getSkProductId());
        if (product == null) {
            throw new BizException(ResultEnum.PRODUCT_NOT_FOUND);
        }
        if (request.getRequiredStatus() != null) {
            Activity activity = activityMapper.selectOne(
                    new LambdaQueryWrapper<Activity>().eq(Activity::getActivityNo, product.getActivityNo()));
            if (activity == null || activity.getActivityStatus() != request.getRequiredStatus()) {
                throw new BizException(ResultEnum.ACTIVITY_STATUS_ERROR);
            }
        }

        int stockToRestore = sku.getActivityStock();
        BigDecimal originalPrice = sku.getOriginalPrice();

        seckillSkuMapper.deleteById(sku.getId());

        return new RemoveSkuResponse(sku.getSkuNo(), stockToRestore, originalPrice);
    }

    @Override
    public List<SeckillSkuDTO> listBySkProductId(String skProductId) {
        List<SeckillSku> list = seckillSkuMapper.selectList(
                new LambdaQueryWrapper<SeckillSku>().eq(SeckillSku::getSkProductId, skProductId));
        return list.stream().map(this::toDTO).toList();
    }

    private BigDecimal calculateSeckillPrice(BigDecimal originalPrice, int discountType, BigDecimal discountParameter) {
        DiscountType type = DiscountType.values()[discountType];
        return switch (type) {
            case FIXED_PRICE -> discountParameter;
            case DISCOUNT -> originalPrice.multiply(discountParameter);
            case FIXED_REDUCTION -> originalPrice.subtract(discountParameter);
        };
    }

    private SeckillSkuDTO toDTO(SeckillSku sku) {
        SeckillSkuDTO dto = new SeckillSkuDTO();
        dto.setId(sku.getId());
        dto.setSkProductId(sku.getSkProductId());
        dto.setSkuNo(sku.getSkuNo());
        dto.setSkuName(sku.getSkuName());
        dto.setOriginalPrice(sku.getOriginalPrice());
        dto.setSeckillPrice(sku.getSeckillPrice());
        dto.setActivityStock(sku.getActivityStock());
        dto.setPurchaseLimit(sku.getPurchaseLimit());
        return dto;
    }
}
