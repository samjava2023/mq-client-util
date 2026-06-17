package com.db.pwmus.mqclient.spi;

import com.db.pwmus.mqclient.api.MqListener;
import com.db.pwmus.mqclient.api.MqSender;
import com.db.pwmus.mqclient.config.ConnectionConfig;
import com.db.pwmus.mqclient.config.QueueConfig;

public interface MqProvider {
    String type(); // e.g. "IBM_MQ"

    MqSender createSender(ConnectionConfig connectionConfig, QueueConfig queueConfig);
    MqListener createListener(ConnectionConfig connectionConfig, QueueConfig queueConfig);
}

