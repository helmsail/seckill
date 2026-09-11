package com.helmsail.seckill.support.server.sku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.result.SupportResultEnum;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * SKU 服务实现
 */
@Service
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuBizService {

    private final SkuMapper skuMapper;

    @Override
    public SkuDTO getBySkuNo(String skuNo) {
        Sku sku = skuMapper.selectOne(
                new LambdaQueryWrapper<Sku>().eq(Sku::getSkuNo, skuNo));
        if (sku == null) {
            throw new BizException(SupportResultEnum.SKU_NOT_FOUND);
        }
        return toDTO(sku);
    }

    @Override
    public List<SkuDTO> listBySpuNo(String spuNo) {
        List<Sku> list = skuMapper.selectList(
                new LambdaQueryWrapper<Sku>().eq(Sku::getSpuNo, spuNo));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public List<SkuDTO> listBySkuName(String skuName) {
        if (!StringUtils.hasText(skuName)) {
            return List.of();
        }
        List<Sku> list = skuMapper.selectList(
                new LambdaQueryWrapper<Sku>().like(Sku::getSkuName, skuName));
        return list.stream().map(this::toDTO).toList();
    }

    @Override
    public void deductStock(String skuNo, int quantity) {
        if (quantity <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        int rows = skuMapper.deductStock(skuNo, quantity);
        if (rows == 0) {
            // rows=0 二义：SKU 不存在或库存不足（原子扣减 WHERE stock >= quantity）
            throw new BizException(SupportResultEnum.STOCK_INSUFFICIENT.getCode(), "库存不足或 SKU 不存在");
        }
    }

    @Override
    public void addStock(String skuNo, int quantity) {
        if (quantity <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        skuMapper.addStock(skuNo, quantity);
    }

    private SkuDTO toDTO(Sku sku) {
        SkuDTO dto = new SkuDTO();
        dto.setId(sku.getId());
        dto.setSpuNo(sku.getSpuNo());
        dto.setSkuNo(sku.getSkuNo());
        dto.setSkuName(sku.getSkuName());
        dto.setPrice(sku.getPrice());
        dto.setStock(sku.getStock());
        return dto;
    }
}
