package com.helmsail.seckill.base.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helmsail.seckill.base.id.SeckillBusinessPrefix;
import com.helmsail.seckill.base.result.SeckillResultEnum;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.id.SnowflakeIdGenerator;
import com.helmsail.seckill.common.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderBizService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public String createOrder(CreateSeckillOrderRequest request) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(SeckillBusinessPrefix.SECKILL_ORDER.buildNo(snowflakeIdGenerator.nextId()));
        order.setUserId(request.getUserId());
        order.setActivityNo(request.getActivityNo());
        order.setSkuNo(request.getSkuNo());
        order.setQuantity(request.getQuantity());
        order.setTotalAmount(request.getTotalAmount());
        order.setPayAmount(request.getPayAmount());
        order.setOrderStatus(SeckillOrderStatus.PENDING.getCode());
        seckillOrderMapper.insert(order);
        return order.getOrderNo();
    }

    @Override
    public SeckillOrderDTO getByOrderNo(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(SeckillResultEnum.ORDER_NOT_FOUND);
        }
        return toDTO(order);
    }

    @Override
    public PageResult<SeckillOrderDTO> pageByUserId(SeckillOrderPageQuery query) {
        Page<SeckillOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        seckillOrderMapper.selectPage(page,
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getUserId, query.getUserId())
                        .orderByDesc(SeckillOrder::getCreateTime)
                        .orderByDesc(SeckillOrder::getId));
        return new PageResult<>(
                page.getRecords().stream().map(this::toDTO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void paySuccess(String orderNo, String tradeNo) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(SeckillResultEnum.ORDER_NOT_FOUND);
        }
        SeckillOrderStatus currentStatus = SeckillOrderStatus.byCode(order.getOrderStatus());
        if (!SeckillOrderStatus.canTransit(currentStatus, SeckillOrderStatus.PAID)) {
            return;
        }
        // 条件更新：仅 PENDING 可流转，重复回调/关单竞态时影响行数为 0
        int rows = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo)
                .eq(SeckillOrder::getOrderStatus, SeckillOrderStatus.PENDING.getCode())
                .set(SeckillOrder::getOrderStatus, SeckillOrderStatus.PAID.getCode())
                .set(SeckillOrder::getPaidTime, LocalDateTime.now())
                .set(SeckillOrder::getTradeNo, tradeNo));
        if (rows == 0) {
            log.warn("支付状态流转跳过（状态已变更）: orderNo={}", orderNo);
        }
    }

    @Override
    public boolean closeOrder(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(SeckillResultEnum.ORDER_NOT_FOUND);
        }
        SeckillOrderStatus currentStatus = SeckillOrderStatus.byCode(order.getOrderStatus());
        if (!SeckillOrderStatus.canTransit(currentStatus, SeckillOrderStatus.CLOSED)) {
            return false;
        }
        // 条件更新：仅 PENDING 可关闭，已支付/重复关单竞态时影响行数为 0
        int rows = seckillOrderMapper.update(null, new LambdaUpdateWrapper<SeckillOrder>()
                .eq(SeckillOrder::getOrderNo, orderNo)
                .eq(SeckillOrder::getOrderStatus, SeckillOrderStatus.PENDING.getCode())
                .set(SeckillOrder::getOrderStatus, SeckillOrderStatus.CLOSED.getCode()));
        if (rows == 0) {
            log.warn("关单跳过（状态已变更）: orderNo={}", orderNo);
        }
        return rows > 0;
    }

    @Override
    public List<String> listTimeoutOrderNos(int beforeMinutes, int limit) {
        LocalDateTime before = LocalDateTime.now().minusMinutes(beforeMinutes);
        // limit 为服务端内部常量（非外部入参）；分片表按分片生效，返回量最多为 分片数 × limit
        List<SeckillOrder> orders = seckillOrderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getOrderStatus, SeckillOrderStatus.PENDING.getCode())
                        .lt(SeckillOrder::getCreateTime, before)
                        .last("LIMIT " + limit));
        return orders.stream().map(SeckillOrder::getOrderNo).toList();
    }

    @Override
    public List<SeckillOrderDTO> listPaidOrdersSince(int minutesAgo, int limit) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesAgo);
        List<SeckillOrder> orders = seckillOrderMapper.selectList(
                new LambdaQueryWrapper<SeckillOrder>()
                        .eq(SeckillOrder::getOrderStatus, SeckillOrderStatus.PAID.getCode())
                        .ge(SeckillOrder::getPaidTime, since)
                        .last("LIMIT " + limit));
        return orders.stream().map(this::toDTO).toList();
    }

    private SeckillOrderDTO toDTO(SeckillOrder order) {
        SeckillOrderDTO dto = new SeckillOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setActivityNo(order.getActivityNo());
        dto.setSkuNo(order.getSkuNo());
        dto.setQuantity(order.getQuantity());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPayAmount(order.getPayAmount());
        dto.setOrderStatus(SeckillOrderStatus.byCode(order.getOrderStatus()));
        dto.setPaidTime(order.getPaidTime());
        dto.setTradeNo(order.getTradeNo());
        return dto;
    }
}
