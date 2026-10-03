package com.helmsail.seckill.support.server.sku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.result.SupportResultEnum;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuDubboService;
import com.helmsail.seckill.support.api.sku.StockItem;
import lombok.RequiredArgsConstructor;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * SKU 服务实现（Dubbo 暴露）
 *
 * 库存变更仅提供批量接口：域内单事务，整批扣/还或整批回滚（零副作用），
 * 跨域编排的补偿由调用方负责；非幂等写操作，消费方须禁用自动重试
 * （Dubbo 重试由 Consumer 决定，Provider 侧 retries 不生效）。
 */
@Service
@DubboService
@RequiredArgsConstructor
public class SkuDubboServiceImpl implements SkuDubboService {

    private final SkuMapper skuMapper;

    /** 单批库存变更上限（防单事务持锁过长；当前运营选品批量远小于该值） */
    private static final int MAX_BATCH_SIZE = 200;

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
    @Transactional
    public void batchDeductStock(List<StockItem> items) {
        for (StockItem item : validateBatch(items)) {
            int rows = skuMapper.deductStock(item.getSkuNo(), item.getQuantity());
            if (rows == 0) {
                // rows=0 二义：SKU 不存在或库存不足（原子扣减 WHERE stock >= quantity）
                throw new BizException(SupportResultEnum.STOCK_INSUFFICIENT.getCode(),
                        "库存不足或 SKU 不存在: " + item.getSkuNo());
            }
        }
    }

    @Override
    @Transactional
    public void batchAddStock(List<StockItem> items) {
        for (StockItem item : validateBatch(items)) {
            int rows = skuMapper.addStock(item.getSkuNo(), item.getQuantity());
            if (rows == 0) {
                // 加库存无数量条件：rows=0 单义——行不存在（SKU 被删或编号错误）
                throw new BizException(SupportResultEnum.SKU_NOT_FOUND.getCode(),
                        "SKU 不存在或已删除: " + item.getSkuNo());
            }
        }
    }

    /**
     * 批量入参校验：非空、数量上限、SKU 非空且不重复、数量为正
     */
    private List<StockItem> validateBatch(List<StockItem> items) {
        if (items == null || items.isEmpty()) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "库存变更列表不能为空");
        }
        if (items.size() > MAX_BATCH_SIZE) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "单批库存变更不能超过 " + MAX_BATCH_SIZE + " 条");
        }
        Set<String> skuNos = new HashSet<>();
        for (StockItem item : items) {
            if (item == null || !StringUtils.hasText(item.getSkuNo()) || item.getQuantity() <= 0) {
                throw new BizException(ResultEnum.PARAM_ERROR);
            }
            if (!skuNos.add(item.getSkuNo())) {
                throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "批量列表存在重复 SKU: " + item.getSkuNo());
            }
        }
        return items;
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
