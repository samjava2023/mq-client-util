package com.db.pwmus.mqclient.ibmmq;

import com.db.pwmus.mqclient.api.MessageFormat;
import com.db.pwmus.mqclient.config.ConnectionConfig;
import com.db.pwmus.mqclient.config.QueueConfig;
import com.db.pwmus.mqclient.support.MqFlowLog;

import java.util.Map;

final class MqSender implements com.db.pwmus.mqclient.api.MqSender {
    private static final String CLASS = "MqSender";

    private final ConnectionConfig connectionConfig;
    private final QueueConfig queueConfig;

    MqSender(ConnectionConfig connectionConfig, QueueConfig queueConfig) {
        this.connectionConfig = connectionConfig;
        this.queueConfig = queueConfig;
    }

    @Override
    public void sendJson(String body) {
        sendJson((Object) body, null);
    }

    @Override
    public void sendXml(String body) {
        sendXml((Object) body, null);
    }

    @Override
    public void sendJson(Object value) {
        sendJson(value, null);
    }

    @Override
    public void sendXml(Object value) {
        sendXml(value, null);
    }

    @Override
    public void sendJson(Object value, Map<String, String> headers) {
        String body = asBody(value);
        MqFlowLog.enter(CLASS, "sendJson", "physicalQueue=" + queueConfig.getResolvedName()
            + ", contentType=" + MessageFormat.JSON.contentType()
            + ", bodyLength=" + body.length()
            + ", body=" + MqFlowLog.formatBody(body)
            + ", headers=" + MqFlowLog.formatHeaders(headers)
            + " | " + MqFlowLog.formatConnection(null, connectionConfig)
            + " | " + MqFlowLog.formatQueue(null, queueConfig));
        try {
            IbmMqJmsSupport.send(connectionConfig, queueConfig, body, MessageFormat.JSON, headers);
            MqFlowLog.success(CLASS, "sendJson", "physicalQueue=" + queueConfig.getResolvedName()
                + ", bodyLength=" + body.length());
        } catch (RuntimeException e) {
            MqFlowLog.fail(CLASS, "sendJson", "physicalQueue=" + queueConfig.getResolvedName(), e);
            throw e;
        }
    }

    @Override
    public void sendXml(Object value, Map<String, String> headers) {
        String body = asBody(value);
        MqFlowLog.enter(CLASS, "sendXml", "physicalQueue=" + queueConfig.getResolvedName()
            + ", contentType=" + MessageFormat.XML.contentType()
            + ", bodyLength=" + body.length()
            + ", body=" + MqFlowLog.formatBody(body)
            + ", headers=" + MqFlowLog.formatHeaders(headers)
            + " | " + MqFlowLog.formatConnection(null, connectionConfig)
            + " | " + MqFlowLog.formatQueue(null, queueConfig));
        try {
            IbmMqJmsSupport.send(connectionConfig, queueConfig, body, MessageFormat.XML, headers);
            MqFlowLog.success(CLASS, "sendXml", "physicalQueue=" + queueConfig.getResolvedName()
                + ", bodyLength=" + body.length());
        } catch (RuntimeException e) {
            MqFlowLog.fail(CLASS, "sendXml", "physicalQueue=" + queueConfig.getResolvedName(), e);
            throw e;
        }
    }

    private static String asBody(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        return String.valueOf(value);
    }
}
