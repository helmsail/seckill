package com.helmsail.seckill.base.order;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.helmsail.seckill.base.id.SeckillBusinessPrefix;
import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.id.SnowflakeIdGenerator;
import com.helmsail.seckill.common.result.ResultEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 秒杀订单服务实现
 */
@Service
@RequiredArgsConstructor
public class SeckillOrderServiceImpl implements SeckillOrderBizService {

    private final SeckillOrderMapper seckillOrderMapper;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    public String createOrder(CreateSeckillOrderRequest request) {
        SeckillOrder order = new SeckillOrder();
        order.setOrderNo(String.valueOf(SeckillBusinessPrefix.SECKILL_ORDER.getPrefix()) + snowflakeIdGenerator.nextId());
        order.setUserId(request.getUserId());
        order.setTotalAmount(request.getTotalAmount());
        order.setPayAmount(request.getPayAmount());
        order.setOrderStatus(SeckillOrderStatus.PENDING.getCode());
        order.setRemark(request.getRemark());
        seckillOrderMapper.insert(order);
        return order.getOrderNo();
    }

    @Override
    public SeckillOrderDTO getByOrderNo(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        return toDTO(order);
    }

    @Override
    public SeckillOrderPageResult pageByUserId(SeckillOrderPageQuery query) {
        Page<SeckillOrder> page = new Page<>(query.getPageNum(), query.getPageSize());
        seckillOrderMapper.selectPage(page,
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getUserId, query.getUserId()));
        return new SeckillOrderPageResult(
                page.getRecords().stream().map(this::toDTO).toList(),
                page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public void paySuccess(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        order.setOrderStatus(SeckillOrderStatus.PAID.getCode());
        order.setPaidTime(LocalDateTime.now());
        seckillOrderMapper.updateById(order);
    }

    @Override
    public void closeOrder(String orderNo) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                new LambdaQueryWrapper<SeckillOrder>().eq(SeckillOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new BizException(ResultEnum.NOT_FOUND);
        }
        order.setOrderStatus(SeckillOrderStatus.CLOSED.getCode());
        seckillOrderMapper.updateById(order);
    }

    private SeckillOrderDTO toDTO(SeckillOrder order) {
        SeckillOrderDTO dto = new SeckillOrderDTO();
        dto.setId(order.getId());
        dto.setOrderNo(order.getOrderNo());
        dto.setUserId(order.getUserId());
        dto.setTotalAmount(order.getTotalAmount());
        dto.setPayAmount(order.getPayAmount());
        dto.setOrderStatus(SeckillOrderStatus.values()[order.getOrderStatus()]);
        dto.setPaidTime(order.getPaidTime());
        dto.setRemark(order.getRemark());
        return dto;
    }
}
