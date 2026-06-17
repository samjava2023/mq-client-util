package com.db.pwmus.mqclient.ibmmq;

import com.db.pwmus.mqclient.config.ConnectionConfig;
import com.db.pwmus.mqclient.config.QueueConfig;
import com.db.pwmus.mqclient.spi.MqProvider;
import com.db.pwmus.mqclient.support.MqFlowLog;

public final class IbmMqProvider implements MqProvider {
    private static final String CLASS = "IbmMqProvider";
    public static final String TYPE = "IBM_MQ";

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public com.db.pwmus.mqclient.api.MqSender createSender(ConnectionConfig connectionConfig, QueueConfig queueConfig) {
        MqFlowLog.info(CLASS, "createSender", MqFlowLog.formatQueue(null, queueConfig)
            + " | " + MqFlowLog.formatConnection(null, connectionConfig));
        return new MqSender(connectionConfig, queueConfig);
    }

    @Override
    public com.db.pwmus.mqclient.api.MqListener createListener(ConnectionConfig connectionConfig, QueueConfig queueConfig) {
        MqFlowLog.info(CLASS, "createListener", MqFlowLog.formatQueue(null, queueConfig)
            + " | " + MqFlowLog.formatConnection(null, connectionConfig));
        return new MqListener(connectionConfig, queueConfig);
    }
}
