package com.db.pwmus.mqclient.config;

import java.util.Collections;
import java.util.Map;

public final class ConnectionConfig {
    private final String type;
    private final String host;
    private final Integer port;
    private final String username;
    private final String password;

    // IBM MQ specific
    private final String queueManager;
    private final String channel;
    private final String connectionName;
    private final String sslCipherSpec;
    private final Boolean useMqCspAuthentication;
    private final String sslTrustStore;
    private final String sslTrustStorePassword;
    private final String sslKeyStore;
    private final String sslKeyStorePassword;

    // RabbitMQ specific (ignored for IBM MQ)
    private final String virtualHost;

    // misc
    private final Boolean ssl;
    private final Integer connectionTimeoutMs;
    private final Map<String, Object> properties;

    public ConnectionConfig(String type,
                            String host,
                            Integer port,
                            String username,
                            String password,
                            String queueManager,
                            String channel,
                            String connectionName,
                            String sslCipherSpec,
                            Boolean useMqCspAuthentication,
                            String sslTrustStore,
                            String sslTrustStorePassword,
                            String sslKeyStore,
                            String sslKeyStorePassword,
                            String virtualHost,
                            Boolean ssl,
                            Integer connectionTimeoutMs,
                            Map<String, Object> properties) {
        this.type = type;
        this.connectionName = connectionName;
        this.sslCipherSpec = sslCipherSpec;

        String resolvedHost = host;
        Integer resolvedPort = port;
        if ((resolvedHost == null || resolvedPort == null) && connectionName != null) {
            HostPort hp = parseConnectionName(connectionName);
            if (resolvedHost == null) {
                resolvedHost = hp.host;
            }
            if (resolvedPort == null) {
                resolvedPort = hp.port;
            }
        }

        this.host = resolvedHost;
        this.port = resolvedPort;
        this.username = username;
        this.password = password;
        this.queueManager = queueManager;
        this.channel = channel;
        this.useMqCspAuthentication = useMqCspAuthentication;
        this.sslTrustStore = sslTrustStore;
        this.sslTrustStorePassword = sslTrustStorePassword;
        this.sslKeyStore = sslKeyStore;
        this.sslKeyStorePassword = sslKeyStorePassword;
        this.virtualHost = virtualHost;
        this.ssl = ssl;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.properties = properties == null ? Collections.<String, Object>emptyMap() : Collections.unmodifiableMap(properties);
    }

    public String getType() { return type; }
    public String getHost() { return host; }
    public Integer getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    public String getQueueManager() { return queueManager; }
    public String getChannel() { return channel; }
    public String getConnectionName() { return connectionName; }
    public String getSslCipherSpec() { return sslCipherSpec; }
    public Boolean getUseMqCspAuthentication() { return useMqCspAuthentication; }
    public String getSslTrustStore() { return sslTrustStore; }
    public String getSslTrustStorePassword() { return sslTrustStorePassword; }
    public String getSslKeyStore() { return sslKeyStore; }
    public String getSslKeyStorePassword() { return sslKeyStorePassword; }

    public String getVirtualHost() { return virtualHost; }

    public Boolean getSsl() { return ssl; }
    public Integer getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public Map<String, Object> getProperties() { return properties; }

    /**
     * IBM MQ connection name format: host(port), e.g. localhost(1423)
     */
    private static HostPort parseConnectionName(String connectionName) {
        if (connectionName == null) {
            return new HostPort(null, null);
        }
        String trimmed = connectionName.trim();
        int open = trimmed.indexOf('(');
        int close = trimmed.indexOf(')');
        if (open > 0 && close > open) {
            String h = trimmed.substring(0, open).trim();
            String p = trimmed.substring(open + 1, close).trim();
            try {
                return new HostPort(h, Integer.valueOf(p));
            } catch (NumberFormatException e) {
                return new HostPort(h, null);
            }
        }
        return new HostPort(trimmed, null);
    }

    private static final class HostPort {
        private final String host;
        private final Integer port;

        private HostPort(String host, Integer port) {
            this.host = host;
            this.port = port;
        }
    }
}

