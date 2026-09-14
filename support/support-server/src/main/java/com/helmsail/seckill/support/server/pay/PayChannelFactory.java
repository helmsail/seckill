package com.helmsail.seckill.support.server.pay;

import com.helmsail.seckill.common.exception.BizException;
import com.helmsail.seckill.common.result.ResultEnum;
import com.helmsail.seckill.support.api.pay.PayChannelType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 支付渠道工厂
 *
 * 启动时登记容器内全部渠道实现（构造器集合注入），运行时按渠道类型取实例；
 * 新增渠道仅需实现 PayChannel 并声明为 Bean，注册自动完成。
 */
@Component
public class PayChannelFactory {

    private final Map<PayChannelType, PayChannel> channelMap;

    public PayChannelFactory(List<PayChannel> channels) {
        this.channelMap = new EnumMap<>(PayChannelType.class);
        for (PayChannel channel : channels) {
            channelMap.put(channel.getChannelType(), channel);
        }
    }

    /**
     * 取指定渠道实现
     */
    public PayChannel getChannel(PayChannelType channelType) {
        PayChannel channel = channelMap.get(channelType);
        if (channel == null) {
            throw new BizException(ResultEnum.PARAM_ERROR.getCode(), "不支持的支付渠道: " + channelType);
        }
        return channel;
    }
}
