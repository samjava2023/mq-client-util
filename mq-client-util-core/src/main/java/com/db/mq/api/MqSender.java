package com.db.pwmus.mqclient.api;

import java.util.Map;

public interface MqSender {
    void sendJson(String body);
    void sendXml(String body);

    void sendJson(Object value);
    void sendXml(Object value);

    void sendJson(Object value, Map<String, String> headers);
    void sendXml(Object value, Map<String, String> headers);
}

