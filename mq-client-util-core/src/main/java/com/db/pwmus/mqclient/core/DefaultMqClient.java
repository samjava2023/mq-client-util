package com.db.pwmus.mqclient.core;

import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.api.MqSender;
import com.db.pwmus.mqclient.listener.MqListenerRegistry;

import java.util.Map;
import java.util.concurrent.TimeUnit;

final class DefaultMqClient implements MqClient {
    private final MqClientFactory factory;
    private final MqListenerRegistry listenerRegistry;

    DefaultMqClient(MqClientFactory factory) {
        this.factory = factory;
        this.listenerRegistry = new MqListenerRegistry(factory);
    }

    @Override
    public void sendJson(String logicalQueue, String body) {
        factory.sender(logicalQueue).sendJson(body);
    }

    @Override
    public void sendXml(String logicalQueue, String body) {
        factory.sender(logicalQueue).sendXml(body);
    }

    @Override
    public void sendJson(String logicalQueue, Object value) {
        factory.sender(logicalQueue).sendJson(value);
    }

    @Override
    public void sendXml(String logicalQueue, Object value) {
        factory.sender(logicalQueue).sendXml(value);
    }

    @Override
    public void sendJson(String logicalQueue, Object value, Map<String, String> headers) {
        factory.sender(logicalQueue).sendJson(value, headers);
    }

    @Override
    public void sendXml(String logicalQueue, Object value, Map<String, String> headers) {
        MqSender sender = factory.sender(logicalQueue);
        sender.sendXml(value, headers);
    }

    @Override
    public MqMessage receive(String logicalQueue, long timeout, TimeUnit unit) {
        return factory.listener(logicalQueue).receive(timeout, unit);
    }

    @Override
    public MqListenerRegistry listeners() {
        return listenerRegistry;
    }

    @Override
    public MqClientFactory factory() {
        return factory;
    }

    @Override
    public void close() {
        listenerRegistry.close();
        factory.close();
    }
}
