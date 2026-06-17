package com.db.pwmus.mqclient.api;

public enum MessageFormat {
    JSON("application/json"),
    XML("application/xml");

    private final String contentType;

    MessageFormat(String contentType) {
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }
}

