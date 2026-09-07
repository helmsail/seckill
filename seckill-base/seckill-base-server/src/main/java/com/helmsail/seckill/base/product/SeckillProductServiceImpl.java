package com.helmsail.seckill.base.product;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 秒杀商品服务实现
 */
@Service
@RequiredArgsConstructor
public class SeckillProductServiceImpl implements SeckillProductBizService {

    private final SeckillProductMapper seckillProductMapper;

    @Override
    public void addToActivity(AddProductRequest request) {
        SeckillProduct product = new SeckillProduct();
        product.setActivityNo(request.getActivityNo());
        product.setSpuNo(request.getSpuNo());
        product.setSpuName(request.getSpuName());
        product.setDiscountType(request.getDiscountType().getCode());
        product.setDiscountParameter(request.getDiscountParameter());
        product.setSortOrder(request.getSortOrder());
        seckillProductMapper.insert(product);
    }

    @Override
    public void removeFromActivity(String activityNo, String spuNo) {
        int rows = seckillProductMapper.delete(
                new LambdaQueryWrapper<SeckillProduct>()
                        .eq(SeckillProduct::getActivityNo, activityNo)
                        .eq(SeckillProduct::getSpuNo, spuNo));
        if (rows == 0) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
    }

    @Override
    public List<SeckillProductDTO> listByActivityNo(String activityNo) {
        List<SeckillProduct> list = seckillProductMapper.selectList(
                new LambdaQueryWrapper<SeckillProduct>()
                        .eq(SeckillProduct::getActivityNo, activityNo)
                        .orderByAsc(SeckillProduct::getSortOrder));
        return list.stream().map(this::toDTO).toList();
    }

    private SeckillProductDTO toDTO(SeckillProduct product) {
        SeckillProductDTO dto = new SeckillProductDTO();
        dto.setId(product.getId());
        dto.setActivityNo(product.getActivityNo());
        dto.setSpuNo(product.getSpuNo());
        dto.setSpuName(product.getSpuName());
        dto.setDiscountType(DiscountType.values()[product.getDiscountType()]);
        dto.setDiscountParameter(product.getDiscountParameter());
        dto.setSortOrder(product.getSortOrder());
        return dto;
    }
}
