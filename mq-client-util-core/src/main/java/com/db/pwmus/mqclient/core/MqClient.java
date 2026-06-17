package com.db.pwmus.mqclient.core;

import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.listener.MqListenerRegistry;

import java.io.Closeable;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Facade over {@link MqClientFactory} for send/receive operations by logical queue name.
 */
public interface MqClient extends Closeable {

    static MqClientBuilder builder() {
        return new MqClientBuilder();
    }

    void sendJson(String logicalQueue, String body);

    void sendXml(String logicalQueue, String body);

    void sendJson(String logicalQueue, Object value);

    void sendXml(String logicalQueue, Object value);

    void sendJson(String logicalQueue, Object value, Map<String, String> headers);

    void sendXml(String logicalQueue, Object value, Map<String, String> headers);

    MqMessage receive(String logicalQueue, long timeout, TimeUnit unit);

    MqListenerRegistry listeners();

    MqClientFactory factory();
}
