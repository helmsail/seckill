package com.helmsail.seckill.support.server.sku;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.result.SupportResultEnum;
import com.helmsail.seckill.support.api.sku.SkuDTO;
import com.helmsail.seckill.support.api.sku.SkuService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * SKU 服务实现（Dubbo 暴露）
 *
 * retries = 0：库存扣减/归还通过 requestId + 流水表（uk_request_id）实现幂等，
 * 自动重试/调用方重放均不会造成库存错账；流水与库存更新同事务（失败回滚不占幂等键）
 */
@Slf4j
@Service
@DubboService(retries = 0)
@RequiredArgsConstructor
public class SkuServiceImpl implements SkuService {

    /** 变更类型：扣减 */
    private static final int CHANGE_TYPE_DEDUCT = 1;

    /** 变更类型：归还 */
    private static final int CHANGE_TYPE_RESTORE = 2;

    private final SkuMapper skuMapper;
    private final StockLogMapper stockLogMapper;

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
    @Transactional
    public void deductStock(String skuNo, int quantity, String requestId) {
        if (quantity <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        if (!StringUtils.hasText(requestId)) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "requestId 不能为空");
        }
        // 幂等闸：同 requestId 已成功处理过则直接返回（重试/重放安全）
        if (!recordLog(requestId, skuNo, CHANGE_TYPE_DEDUCT, quantity)) {
            return;
        }
        int rows = skuMapper.deductStock(skuNo, quantity);
        if (rows == 0) {
            // rows=0 二义：SKU 不存在或库存不足（原子扣减 WHERE stock >= quantity）；
            // 抛错回滚事务（流水一并回滚，失败请求不占用幂等键）
            throw new BizException(SupportResultEnum.STOCK_INSUFFICIENT.getCode(), "库存不足或 SKU 不存在");
        }
    }

    @Override
    @Transactional
    public void addStock(String skuNo, int quantity, String requestId) {
        if (quantity <= 0) {
            throw new BizException(ResultEnum.PARAM_ERROR);
        }
        if (!StringUtils.hasText(requestId)) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "requestId 不能为空");
        }
        if (!recordLog(requestId, skuNo, CHANGE_TYPE_RESTORE, quantity)) {
            return;
        }
        skuMapper.addStock(skuNo, quantity);
    }

    /**
     * 幂等闸：记录变更流水（uk_request_id 唯一约束）
     *
     * @return true=首次处理（继续执行业务），false=已处理过（幂等返回）
     */
    private boolean recordLog(String requestId, String skuNo, int changeType, int quantity) {
        StockLog stockLog = new StockLog();
        stockLog.setRequestId(requestId);
        stockLog.setSkuNo(skuNo);
        stockLog.setChangeType(changeType);
        stockLog.setQuantity(quantity);
        try {
            stockLogMapper.insert(stockLog);
            return true;
        } catch (DuplicateKeyException e) {
            log.warn("库存操作幂等命中: requestId={}, skuNo={}, changeType={}", requestId, skuNo, changeType);
            return false;
        }
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
