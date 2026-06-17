package com.db.pwmus.mqclient.listener;

import com.db.pwmus.mqclient.api.MqMessageHandler;

/**
 * Strategy for handling messages on a specific logical queue (Open/Closed: add handlers without changing the registry).
 */
public interface MqQueueMessageHandler extends MqMessageHandler {

    String getLogicalQueueName();
}
