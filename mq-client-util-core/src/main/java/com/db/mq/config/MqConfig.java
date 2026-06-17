package com.db.pwmus.mqclient.config;

import java.util.Collections;
import java.util.Map;

public final class MqConfig {
    private final String defaultConnection;
    private final Map<String, ConnectionConfig> connections;
    private final Map<String, QueueConfig> queues;

    public MqConfig(String defaultConnection,
                    Map<String, ConnectionConfig> connections,
                    Map<String, QueueConfig> queues) {
        this.defaultConnection = defaultConnection;
        this.connections = connections == null ? Collections.<String, ConnectionConfig>emptyMap() : Collections.unmodifiableMap(connections);
        this.queues = queues == null ? Collections.<String, QueueConfig>emptyMap() : Collections.unmodifiableMap(queues);
    }

    public String getDefaultConnection() {
        return defaultConnection;
    }

    public Map<String, ConnectionConfig> getConnections() {
        return connections;
    }

    public Map<String, QueueConfig> getQueues() {
        return queues;
    }
}

