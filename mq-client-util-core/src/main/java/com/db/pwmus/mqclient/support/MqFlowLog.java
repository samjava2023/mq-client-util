package com.db.pwmus.mqclient.support;

import com.db.pwmus.mqclient.config.ConnectionConfig;
import com.db.pwmus.mqclient.config.QueueConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Structured flow logging for mq-client-util. Logger name: {@code mq-client.flow}
 */
public final class MqFlowLog {
    private static final Logger LOG = LoggerFactory.getLogger("mq-client.flow");
    private static final int BODY_PREVIEW_MAX = 500;

    private MqFlowLog() {
    }

    public static void enter(String className, String method, String details) {
        LOG.info("[ENTER] {}.{} | {}", className, method, details);
    }

    public static void success(String className, String method, String details) {
        LOG.info("[SUCCESS] {}.{} | {}", className, method, details);
    }

    public static void info(String className, String method, String details) {
        LOG.info("[INFO] {}.{} | {}", className, method, details);
    }

    public static void debug(String className, String method, String details) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("[DEBUG] {}.{} | {}", className, method, details);
        }
    }

    public static void fail(String className, String method, String details, Throwable error) {
        LOG.error("[FAIL] {}.{} | {}", className, method, details, error);
    }

    public static String formatConnection(String connectionName, ConnectionConfig config) {
        if (config == null) {
            return "connectionName=" + connectionName + ", config=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("connectionName=").append(connectionName);
        sb.append(", type=").append(config.getType());
        sb.append(", host=").append(config.getHost());
        sb.append(", port=").append(config.getPort());
        sb.append(", connectionName=").append(config.getConnectionName());
        sb.append(", queueManager=").append(config.getQueueManager());
        sb.append(", channel=").append(config.getChannel());
        sb.append(", ssl=").append(config.getSsl());
        sb.append(", sslCipherSpec=").append(config.getSslCipherSpec());
        sb.append(", sslTrustStore=").append(config.getSslTrustStore());
        sb.append(", sslKeyStore=").append(config.getSslKeyStore());
        sb.append(", username=").append(config.getUsername());
        sb.append(", password=").append(maskSecret(config.getPassword()));
        sb.append(", useMqCspAuthentication=").append(config.getUseMqCspAuthentication());
        sb.append(", connectionTimeoutMs=").append(config.getConnectionTimeoutMs());
        return sb.toString();
    }

    public static String formatQueue(String logicalQueueName, QueueConfig config) {
        if (config == null) {
            return "logicalQueue=" + logicalQueueName + ", config=null";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("logicalQueue=").append(logicalQueueName);
        sb.append(", physicalQueue=").append(config.getResolvedName());
        sb.append(", connectionRef=").append(config.getConnection());
        sb.append(", qname=").append(config.getQname());
        sb.append(", queueType=").append(config.getQueueType());
        sb.append(", defaultContentType=").append(config.getDefaultContentType());
        sb.append(", replyTo=").append(config.getReplyTo());
        sb.append(", listen=").append(config.getListen());
        return sb.toString();
    }

    public static String formatBody(String body) {
        if (body == null) {
            return "null";
        }
        if (body.length() <= BODY_PREVIEW_MAX) {
            return body;
        }
        return body.substring(0, BODY_PREVIEW_MAX) + "...(truncated,len=" + body.length() + ")";
    }

    public static String formatHeaders(Map<String, String> headers) {
        if (headers == null || headers.isEmpty()) {
            return "{}";
        }
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (!first) {
                sb.append(", ");
            }
            first = false;
            String key = entry.getKey();
            String value = entry.getValue();
            if (isSensitiveHeader(key)) {
                value = maskSecret(value);
            }
            sb.append(key).append("=").append(value);
        }
        sb.append("}");
        return sb.toString();
    }

    private static boolean isSensitiveHeader(String key) {
        if (key == null) {
            return false;
        }
        String lower = key.toLowerCase();
        return lower.contains("password") || lower.contains("secret") || lower.contains("token");
    }

    private static String maskSecret(String value) {
        if (value == null || value.length() == 0) {
            return "";
        }
        return "****";
    }
}
