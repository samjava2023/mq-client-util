package com.db.pwmus.mqclient.config;

import java.util.Collections;
import java.util.Map;

public final class QueueConfig {
    private final String connection;
    private final String name;
    private final String qname;
    private final String queueType;
    private final String defaultContentType;
    private final String replyTo;
    private final Boolean listen;

    // RabbitMQ specific (ignored for IBM MQ)
    private final String exchange;
    private final String routingKey;

    // Provider-specific nested configs (optional)
    private final Map<String, Object> ibmMq;

    public QueueConfig(String connection,
                       String name,
                       String qname,
                       String queueType,
                       String defaultContentType,
                       String replyTo,
                       Boolean listen,
                       String exchange,
                       String routingKey,
                       Map<String, Object> ibmMq) {
        this.connection = connection;
        this.qname = qname;
        this.name = name != null ? name : qname;
        this.queueType = queueType;
        this.defaultContentType = defaultContentType;
        this.replyTo = replyTo;
        this.listen = listen;
        this.exchange = exchange;
        this.routingKey = routingKey;
        this.ibmMq = ibmMq == null ? Collections.<String, Object>emptyMap() : Collections.unmodifiableMap(ibmMq);
    }

    public String getConnection() { return connection; }
    public String getName() { return name; }
    public String getQname() { return qname; }

    /** Physical queue name: uses {@code name} or falls back to {@code qname}. */
    public String getResolvedName() {
        if (name != null && name.trim().length() > 0) {
            return name.trim();
        }
        if (qname != null && qname.trim().length() > 0) {
            return qname.trim();
        }
        throw new IllegalArgumentException("Queue config must define 'name' or 'qname'");
    }
    public String getQueueType() { return queueType; }
    public String getDefaultContentType() { return defaultContentType; }
    public String getReplyTo() { return replyTo; }
    public Boolean getListen() { return listen; }

    public String getExchange() { return exchange; }
    public String getRoutingKey() { return routingKey; }

    public Map<String, Object> getIbmMq() { return ibmMq; }
}

