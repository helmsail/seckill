package com.helmsail.seckill.support.server.sku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuPageQuery;
import com.helmsail.seckill.support.api.sku.SkuPageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
            throw new BizException(ResultEnum.NOT_FOUND);
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
    public void deductStock(String skuNo, int quantity) {
        if (quantity <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        int rows = skuMapper.deductStock(skuNo, quantity);
        if (rows == 0) {
            throw new BizException(ResultEnum.STOCK_INSUFFICIENT);
        }
    }

    @Override
    public void addStock(String skuNo, int quantity) {
        if (quantity <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        skuMapper.addStock(skuNo, quantity);
    }

    @Override
    public SkuPageResult page(SkuPageQuery query) {
        Page<Sku> page = new Page<>(query.getPageNum(), query.getPageSize());
        LambdaQueryWrapper<Sku> wrapper = new LambdaQueryWrapper<>();
        if (query.getSpuNo() != null) {
            wrapper.eq(Sku::getSpuNo, query.getSpuNo());
        }
        skuMapper.selectPage(page, wrapper);
        List<SkuDTO> list = page.getRecords().stream().map(this::toDTO).toList();
        return new SkuPageResult(list, page.getTotal(), page.getCurrent(), page.getSize());
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
