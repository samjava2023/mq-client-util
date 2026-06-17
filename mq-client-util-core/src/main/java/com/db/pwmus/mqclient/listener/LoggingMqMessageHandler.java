package com.db.pwmus.mqclient.listener;

import com.db.pwmus.mqclient.api.MqMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Default handler that logs received messages (used when no app-specific handler is registered).
 */
public final class LoggingMqMessageHandler implements MqQueueMessageHandler {
    private static final Logger LOG = LoggerFactory.getLogger(LoggingMqMessageHandler.class);

    private final String logicalQueue;

    private LoggingMqMessageHandler(String logicalQueue) {
        this.logicalQueue = logicalQueue;
    }

    public static LoggingMqMessageHandler forQueue(String logicalQueue) {
        return new LoggingMqMessageHandler(logicalQueue);
    }

    @Override
    public String getLogicalQueueName() {
        return logicalQueue;
    }

    @Override
    public void onMessage(MqMessage message) {
        LOG.info("MQ message received from queue '{}': contentType={}, body={}",
            logicalQueue,
            message.getContentType(),
            message.getBody());
    }
}
