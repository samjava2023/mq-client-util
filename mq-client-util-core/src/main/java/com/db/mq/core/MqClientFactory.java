package com.db.pwmus.mqclient.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.db.pwmus.mqclient.api.MqListener;
import com.db.pwmus.mqclient.api.MqSender;
import com.db.pwmus.mqclient.config.ConnectionConfig;
import com.db.pwmus.mqclient.config.MqConfig;
import com.db.pwmus.mqclient.config.QueueConfig;
import com.db.pwmus.mqclient.spi.MqProvider;
import com.db.pwmus.mqclient.support.MqFlowLog;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring-free factory. Consuming apps may wrap this in a Spring @Bean if desired.
 */
public final class MqClientFactory implements Closeable {
    private static final String CLASS = "MqClientFactory";

    private final MqConfig config;
    private final Map<String, MqProvider> providersByType;
    private final Map<String, MqSender> senderCache = new ConcurrentHashMap<String, MqSender>();

    private MqClientFactory(MqConfig config, Map<String, MqProvider> providersByType) {
        this.config = config;
        this.providersByType = providersByType;
    }

    public static MqClientFactory fromClasspath(String resourceName) {
        MqFlowLog.enter(CLASS, "fromClasspath", "resource=" + resourceName);
        if (resourceName == null || resourceName.trim().isEmpty()) {
            throw new IllegalArgumentException("resourceName is required");
        }
        InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        if (in == null) {
            throw new IllegalArgumentException("Could not find config on classpath: " + resourceName);
        }
        try {
            MqClientFactory factory = fromInputStream(in);
            MqFlowLog.success(CLASS, "fromClasspath", "resource=" + resourceName
                + ", defaultConnection=" + factory.config.getDefaultConnection()
                + ", connections=" + factory.config.getConnections().keySet()
                + ", queues=" + factory.config.getQueues().keySet());
            return factory;
        } finally {
            try { in.close(); } catch (IOException ignore) { /* ignore */ }
        }
    }

    /**
     * Load config from a file on the filesystem (e.g. external path outside the JAR).
     *
     * @param filePath absolute or relative path to mq-config.json
     */
    public static MqClientFactory fromFilePath(String filePath) {
        MqFlowLog.enter(CLASS, "fromFilePath", "filePath=" + filePath);
        if (filePath == null || filePath.trim().isEmpty()) {
            throw new IllegalArgumentException("filePath is required");
        }
        Path path = Paths.get(filePath.trim());
        if (!Files.isRegularFile(path)) {
            throw new IllegalArgumentException("Could not find config file: " + filePath);
        }
        InputStream in = null;
        try {
            in = Files.newInputStream(path);
            MqClientFactory factory = fromInputStream(in);
            MqFlowLog.success(CLASS, "fromFilePath", "filePath=" + filePath
                + ", defaultConnection=" + factory.config.getDefaultConnection()
                + ", connections=" + factory.config.getConnections().keySet()
                + ", queues=" + factory.config.getQueues().keySet());
            return factory;
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to read config file: " + filePath, e);
        } finally {
            if (in != null) {
                try { in.close(); } catch (IOException ignore) { /* ignore */ }
            }
        }
    }

    public static MqClientFactory fromInputStream(InputStream in) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(in);

            String defaultConnection = textOrNull(root.get("defaultConnection"));
            Map<String, ConnectionConfig> connections = parseConnections(root.get("connections"));
            Map<String, QueueConfig> queues = parseQueues(root.get("queues"));

            MqConfig cfg = new MqConfig(defaultConnection, connections, queues);
            Map<String, MqProvider> providers = loadProviders();

            return new MqClientFactory(cfg, providers);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to parse mq-config.json", e);
        }
    }

    public MqSender sender(String logicalQueueName) {
        return senderCache.computeIfAbsent(logicalQueueName, this::createSender);
    }

    private MqSender createSender(String logicalQueueName) {
        MqFlowLog.enter(CLASS, "sender", "logicalQueue=" + logicalQueueName);
        QueueConfig queue = requireQueue(logicalQueueName);
        String connectionName = resolveConnectionName(queue.getConnection());
        ConnectionConfig conn = requireConnection(queue.getConnection());
        MqProvider provider = requireProvider(conn.getType());
        MqFlowLog.info(CLASS, "sender", MqFlowLog.formatQueue(logicalQueueName, queue)
            + " | " + MqFlowLog.formatConnection(connectionName, conn)
            + " | provider=" + provider.type());
        MqSender sender = provider.createSender(conn, queue);
        MqFlowLog.success(CLASS, "sender", "logicalQueue=" + logicalQueueName
            + ", physicalQueue=" + queue.getResolvedName());
        return sender;
    }

    public MqListener listener(String logicalQueueName) {
        MqFlowLog.enter(CLASS, "listener", "logicalQueue=" + logicalQueueName);
        QueueConfig queue = requireQueue(logicalQueueName);
        String connectionName = resolveConnectionName(queue.getConnection());
        ConnectionConfig conn = requireConnection(queue.getConnection());
        MqProvider provider = requireProvider(conn.getType());
        MqFlowLog.info(CLASS, "listener", MqFlowLog.formatQueue(logicalQueueName, queue)
            + " | " + MqFlowLog.formatConnection(connectionName, conn)
            + " | provider=" + provider.type());
        MqListener listener = provider.createListener(conn, queue);
        MqFlowLog.success(CLASS, "listener", "logicalQueue=" + logicalQueueName
            + ", physicalQueue=" + queue.getResolvedName());
        return listener;
    }

    /**
     * Logical queue names with {@code "listen": true} in mq-config.json.
     */
    public List<String> getListenerQueueNames() {
        List<String> names = new ArrayList<String>();
        for (Map.Entry<String, QueueConfig> entry : config.getQueues().entrySet()) {
            QueueConfig queue = entry.getValue();
            if (queue != null && Boolean.TRUE.equals(queue.getListen())) {
                names.add(entry.getKey());
            }
        }
        return Collections.unmodifiableList(names);
    }

    @Override
    public void close() {
        // v1: senders/listeners are owned by callers and should be closed by provider implementations if needed
    }

    private QueueConfig requireQueue(String logicalQueueName) {
        if (logicalQueueName == null || logicalQueueName.trim().isEmpty()) {
            throw new IllegalArgumentException("logicalQueueName is required");
        }
        QueueConfig q = config.getQueues().get(logicalQueueName);
        if (q == null) {
            throw new IllegalArgumentException("Unknown queue logical name: " + logicalQueueName);
        }
        return q;
    }

    private String resolveConnectionName(String connectionName) {
        String resolved = connectionName;
        if (resolved == null || resolved.trim().isEmpty()) {
            resolved = config.getDefaultConnection();
        }
        return resolved;
    }

    private ConnectionConfig requireConnection(String connectionName) {
        String resolved = resolveConnectionName(connectionName);
        if (resolved == null || resolved.trim().isEmpty()) {
            throw new IllegalArgumentException("Queue does not specify connection and defaultConnection is not set");
        }
        ConnectionConfig c = config.getConnections().get(resolved);
        if (c == null) {
            throw new IllegalArgumentException("Unknown connection: " + resolved);
        }
        return c;
    }

    private MqProvider requireProvider(String type) {
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("connection.type is required");
        }
        MqProvider p = providersByType.get(type);
        if (p == null) {
            throw new IllegalArgumentException("No provider found for type: " + type + " (is the provider module on the classpath?)");
        }
        return p;
    }

    private static Map<String, MqProvider> loadProviders() {
        Map<String, MqProvider> map = new LinkedHashMap<String, MqProvider>();
        ServiceLoader<MqProvider> loader = ServiceLoader.load(MqProvider.class);
        for (MqProvider p : loader) {
            if (p != null && p.type() != null) {
                map.put(p.type(), p);
            }
        }
        return map;
    }

    private static Map<String, ConnectionConfig> parseConnections(JsonNode connectionsNode) {
        Map<String, ConnectionConfig> connections = new LinkedHashMap<String, ConnectionConfig>();
        if (connectionsNode == null || !connectionsNode.isObject()) return connections;

        Iterator<Map.Entry<String, JsonNode>> it = connectionsNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String name = entry.getKey();
            JsonNode n = entry.getValue();

            ConnectionConfig c = new ConnectionConfig(
                textOrNull(n.get("type")),
                textOrNull(n.get("host")),
                intOrNull(n.get("port")),
                textOrNull(n.get("username")),
                textOrNull(n.get("password")),
                textOrNull(n.get("queueManager")),
                textOrNull(n.get("channel")),
                textOrNull(n.get("connectionName")),
                textOrNull(n.get("sslCipherSpec")),
                boolOrNull(n.get("useMqCspAuthentication")),
                textOrNull(n.get("sslTrustStore")),
                textOrNull(n.get("sslTrustStorePassword")),
                textOrNull(n.get("sslKeyStore")),
                textOrNull(n.get("sslKeyStorePassword")),
                textOrNull(n.get("virtualHost")),
                boolOrNull(n.get("ssl")),
                intOrNull(n.get("connectionTimeoutMs")),
                new LinkedHashMap<String, Object>()
            );
            connections.put(name, c);
        }
        return connections;
    }

    private static Map<String, QueueConfig> parseQueues(JsonNode queuesNode) {
        Map<String, QueueConfig> queues = new LinkedHashMap<String, QueueConfig>();
        if (queuesNode == null || !queuesNode.isObject()) return queues;

        Iterator<Map.Entry<String, JsonNode>> it = queuesNode.fields();
        while (it.hasNext()) {
            Map.Entry<String, JsonNode> entry = it.next();
            String logicalName = entry.getKey();
            JsonNode n = entry.getValue();

            QueueConfig q = new QueueConfig(
                textOrNull(n.get("connection")),
                textOrNull(n.get("name")),
                textOrNull(n.get("qname")),
                textOrNull(n.get("queueType")),
                textOrNull(n.get("defaultContentType")),
                textOrNull(n.get("replyTo")),
                boolOrNull(n.get("listen")),
                textOrNull(n.get("exchange")),
                textOrNull(n.get("routingKey")),
                new LinkedHashMap<String, Object>()
            );
            queues.put(logicalName, q);
        }
        return queues;
    }

    private static String textOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : n.asText();
    }

    private static Integer intOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : Integer.valueOf(n.asInt());
    }

    private static Boolean boolOrNull(JsonNode n) {
        return n == null || n.isNull() ? null : Boolean.valueOf(n.asBoolean());
    }
}

