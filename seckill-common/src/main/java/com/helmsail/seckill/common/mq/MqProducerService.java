package com.helmsail.seckill.common.mq;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class MqProducerService {

    private final RocketMQTemplate rocketMQTemplate;

    public <T> void send(String topic, T data) {
        Message<T> message = MessageBuilder.withPayload(data).build();
        rocketMQTemplate.send(topic, message);
        log.info("MQ 消息已发送: topic={}", topic);
    }
}
