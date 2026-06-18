package com.db.pwmus.mqclient.sampleboot14;

import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.listener.MqQueueMessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Background listener for logical queue {@code responseQ} (see mq-config.json {@code listen: true}).
 * Registered automatically by {@link com.db.pwmus.mqclient.spring.MqListenerLifecycle}.
 */
@Component
public class ResponseQueueListener implements MqQueueMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(ResponseQueueListener.class);

    private final ResponseMessageProcessor messageProcessor;

    public ResponseQueueListener(ResponseMessageProcessor messageProcessor) {
        this.messageProcessor = messageProcessor;
    }

    @Override
    public String getLogicalQueueName() {
        return "responseQ";
    }

    @Override
    public void onMessage(MqMessage message) {
        log.info("========== MQ message received ==========");
        log.info("queue        : {}", getLogicalQueueName());
        log.info("contentType  : {}", message.getContentType());
        log.info("headers      : {}", message.getHeaders());
        log.info("body         : {}", message.getBody());
        log.info("=========================================");

        messageProcessor.process(message);
    }
}
