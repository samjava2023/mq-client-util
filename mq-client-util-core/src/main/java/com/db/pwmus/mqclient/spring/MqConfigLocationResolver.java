package com.db.pwmus.mqclient.spring;

import com.db.pwmus.mqclient.core.MqClientFactory;

final class MqConfigLocationResolver {
    private MqConfigLocationResolver() {
    }

    static MqClientFactory loadFactory(String configLocation) {
        String location = configLocation == null ? "" : configLocation.trim();
        if (location.isEmpty()) {
            location = "mq-config.json";
        }
        if (location.startsWith("classpath:")) {
            return MqClientFactory.fromClasspath(location.substring("classpath:".length()));
        }
        if (location.startsWith("file:")) {
            return MqClientFactory.fromFilePath(location.substring("file:".length()));
        }
        return MqClientFactory.fromClasspath(location);
    }
}
