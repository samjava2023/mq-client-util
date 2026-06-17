package com.db.pwmus.mqclient.api;

import java.util.concurrent.TimeUnit;

public interface MqListener {
    void onMessage(MqMessageHandler handler);

    void start();
    void stop();

    MqMessage receive(long timeout, TimeUnit unit);
    MqMessage receiveWhere(String correlationId, long timeout, TimeUnit unit);
}

