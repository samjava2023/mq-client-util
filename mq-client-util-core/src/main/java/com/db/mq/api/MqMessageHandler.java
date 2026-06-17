package com.db.pwmus.mqclient.api;

public interface MqMessageHandler {
    void onMessage(MqMessage message);
}
