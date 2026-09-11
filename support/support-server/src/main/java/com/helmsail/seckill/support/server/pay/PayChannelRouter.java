package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.pay.PayChannelType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道路由
 *
 * 统一入口：按渠道类型分发到对应渠道实现。
 */
@Slf4j
@Component
public class PayChannelRouter {

    private final Map<PayChannelType, PayChannel> channelMap;

    public PayChannelRouter(List<PayChannel> channels) {
        this.channelMap = new EnumMap<>(PayChannelType.class);
        for (PayChannel channel : channels) {
            channelMap.put(channel.getChannelType(), channel);
        }
    }

    /**
     * 路由到指定渠道
     */
    public PayChannel route(PayChannelType channelType) {
        PayChannel channel = channelMap.get(channelType);
        if (channel == null) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "不支持的支付渠道: " + channelType);
        }
        return channel;
    }
}
