package com.db.pwmus.mqclient.api;

import java.util.Collections;
import java.util.Map;

public final class MqMessage {
    private final String body;
    private final String contentType;
    private final Map<String, String> headers;

    public MqMessage(String body, String contentType, Map<String, String> headers) {
        this.body = body;
        this.contentType = contentType;
        this.headers = headers == null ? Collections.<String, String>emptyMap() : Collections.unmodifiableMap(headers);
    }

    public String getBody() {
        return body;
    }

    public String getContentType() {
        return contentType;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }
}

