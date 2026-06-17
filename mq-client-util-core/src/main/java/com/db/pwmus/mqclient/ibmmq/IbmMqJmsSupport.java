package com.db.pwmus.mqclient.ibmmq;

import com.db.pwmus.mqclient.api.MessageFormat;
import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.config.ConnectionConfig;
import com.db.pwmus.mqclient.config.QueueConfig;
import com.db.pwmus.mqclient.support.MqFlowLog;
import com.ibm.mq.jms.MQQueueConnectionFactory;
import com.ibm.msg.client.wmq.WMQConstants;

import javax.jms.Connection;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

final class IbmMqJmsSupport {
    private static final String CLASS = "IbmMqJmsSupport";

    private IbmMqJmsSupport() {
    }

    static void send(ConnectionConfig connectionConfig,
                   QueueConfig queueConfig,
                   String body,
                   MessageFormat format,
                   Map<String, String> headers) {
        String physicalQueue = queueConfig.getResolvedName();
        MqFlowLog.debug(CLASS, "send", "opening JMS connection | physicalQueue=" + physicalQueue
            + ", contentType=" + format.contentType()
            + " | " + MqFlowLog.formatConnection(null, connectionConfig));
        Connection connection = null;
        Session session = null;
        try {
            MQQueueConnectionFactory factory = createConnectionFactory(connectionConfig);
            connection = openConnection(factory, connectionConfig);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(physicalQueue);
            MessageProducer producer = session.createProducer(queue);

            TextMessage message = session.createTextMessage(body);
            message.setStringProperty("contentType", format.contentType());
            applyHeaders(message, headers);

            connection.start();
            MqFlowLog.debug(CLASS, "send", "producer.send | physicalQueue=" + physicalQueue
                + ", bodyLength=" + (body == null ? 0 : body.length())
                + ", headers=" + MqFlowLog.formatHeaders(headers));
            producer.send(message);
            MqFlowLog.debug(CLASS, "send", "producer.send completed | physicalQueue=" + physicalQueue);
        } catch (JMSException e) {
            MqFlowLog.fail(CLASS, "send", "physicalQueue=" + physicalQueue, e);
            throw new IllegalStateException("IBM MQ send failed for queue " + physicalQueue, e);
        } finally {
            closeQuietly(session);
            closeQuietly(connection);
        }
    }

    static MqMessage receive(ConnectionConfig connectionConfig,
                             QueueConfig queueConfig,
                             long timeout,
                             TimeUnit unit) {
        String physicalQueue = queueConfig.getResolvedName();
        MqFlowLog.debug(CLASS, "receive", "opening JMS connection | physicalQueue=" + physicalQueue
            + ", timeout=" + timeout + ", unit=" + unit
            + " | " + MqFlowLog.formatConnection(null, connectionConfig));
        Connection connection = null;
        Session session = null;
        try {
            MQQueueConnectionFactory factory = createConnectionFactory(connectionConfig);
            connection = openConnection(factory, connectionConfig);
            session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            Queue queue = session.createQueue(physicalQueue);
            MessageConsumer consumer = session.createConsumer(queue);

            connection.start();
            long timeoutMs = unit.toMillis(timeout);
            MqFlowLog.debug(CLASS, "receive", "consumer.receive | physicalQueue=" + physicalQueue
                + ", timeoutMs=" + timeoutMs);
            Message message = consumer.receive(timeoutMs);
            if (message == null) {
                MqFlowLog.debug(CLASS, "receive", "no message | physicalQueue=" + physicalQueue);
                return null;
            }
            MqMessage mqMessage = toMqMessage(message);
            MqFlowLog.debug(CLASS, "receive", "message received | physicalQueue=" + physicalQueue
                + ", contentType=" + mqMessage.getContentType()
                + ", bodyLength=" + (mqMessage.getBody() == null ? 0 : mqMessage.getBody().length()));
            return mqMessage;
        } catch (JMSException e) {
            MqFlowLog.fail(CLASS, "receive", "physicalQueue=" + physicalQueue, e);
            throw new IllegalStateException("IBM MQ receive failed for queue " + physicalQueue, e);
        } finally {
            closeQuietly(session);
            closeQuietly(connection);
        }
    }

    private static MQQueueConnectionFactory createConnectionFactory(ConnectionConfig config) throws JMSException {
        if (config.getHost() == null || config.getPort() == null) {
            throw new IllegalArgumentException("host/port or connectionName is required for IBM MQ");
        }
        if (config.getQueueManager() == null) {
            throw new IllegalArgumentException("queueManager is required for IBM MQ");
        }
        if (config.getChannel() == null) {
            throw new IllegalArgumentException("channel is required for IBM MQ");
        }

        applySslStores(config);

        MQQueueConnectionFactory factory = new MQQueueConnectionFactory();
        factory.setHostName(config.getHost());
        factory.setPort(config.getPort());
        factory.setQueueManager(config.getQueueManager());
        factory.setChannel(config.getChannel());
        factory.setTransportType(WMQConstants.WMQ_CM_CLIENT);

        configureAuthentication(factory, config);
        configureSsl(factory, config);

        if (config.getConnectionTimeoutMs() != null) {
            factory.setIntProperty(WMQConstants.WMQ_CLIENT_RECONNECT_TIMEOUT, config.getConnectionTimeoutMs());
        }

        MqFlowLog.debug(CLASS, "createConnectionFactory", "mode=" + describeConnectionMode(config)
            + " | " + MqFlowLog.formatConnection(null, config));

        return factory;
    }

    private static void configureAuthentication(MQQueueConnectionFactory factory, ConnectionConfig config)
            throws JMSException {
        String username = trimToNull(config.getUsername());
        if (username != null) {
            factory.setStringProperty(WMQConstants.USERID, username);
            if (config.getPassword() != null) {
                factory.setStringProperty(WMQConstants.PASSWORD, config.getPassword());
            }
        }

        Boolean useCsp = config.getUseMqCspAuthentication();
        if (useCsp != null && !useCsp.booleanValue()) {
            System.setProperty("com.ibm.mq.cfg.jmqi.useMQCSPauthentication", "N");
            factory.setBooleanProperty(WMQConstants.USER_AUTHENTICATION_MQCSP, false);
        }
    }

    private static void configureSsl(MQQueueConnectionFactory factory, ConnectionConfig config) throws JMSException {
        if (!isSslEnabled(config)) {
            return;
        }

        String cipher = trimToNull(config.getSslCipherSpec());
        if (cipher == null) {
            throw new IllegalArgumentException(
                "sslCipherSpec is required when ssl is true (e.g. ECDHE_RSA_AES_256_GCM_SHA384)");
        }

        factory.setSSLCipherSuite(cipher);
        MqFlowLog.debug(CLASS, "configureSsl", "enabled cipherSuite=" + cipher
            + ", auth=" + (hasCredentials(config) ? "username/password" : "none"));
    }

    private static void applySslStores(ConnectionConfig config) {
        if (!isSslEnabled(config)) {
            return;
        }

        String trustStore = trimToNull(config.getSslTrustStore());
        if (trustStore != null) {
            System.setProperty("javax.net.ssl.trustStore", trustStore);
            String trustStorePassword = config.getSslTrustStorePassword();
            if (trustStorePassword != null) {
                System.setProperty("javax.net.ssl.trustStorePassword", trustStorePassword);
            }
        }

        String keyStore = trimToNull(config.getSslKeyStore());
        if (keyStore != null) {
            System.setProperty("javax.net.ssl.keyStore", keyStore);
            String keyStorePassword = config.getSslKeyStorePassword();
            if (keyStorePassword != null) {
                System.setProperty("javax.net.ssl.keyStorePassword", keyStorePassword);
            }
        }
    }

    private static Connection openConnection(MQQueueConnectionFactory factory, ConnectionConfig config)
            throws JMSException {
        String username = trimToNull(config.getUsername());
        if (username != null) {
            String password = config.getPassword() != null ? config.getPassword() : "";
            return factory.createConnection(username, password);
        }
        return factory.createConnection();
    }

    private static boolean isSslEnabled(ConnectionConfig config) {
        return Boolean.TRUE.equals(config.getSsl());
    }

    private static boolean hasCredentials(ConnectionConfig config) {
        return trimToNull(config.getUsername()) != null;
    }

    private static String describeConnectionMode(ConnectionConfig config) {
        String transport = isSslEnabled(config) ? "ssl" : "plain";
        String auth = hasCredentials(config) ? "userpass" : "no-auth";
        return transport + "+" + auth;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void applyHeaders(TextMessage message, Map<String, String> headers) throws JMSException {
        if (headers == null) {
            return;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            message.setStringProperty(entry.getKey(), entry.getValue());
        }
    }

    private static MqMessage toMqMessage(Message message) throws JMSException {
        String body = "";
        if (message instanceof TextMessage) {
            body = ((TextMessage) message).getText();
        }

        String contentType = message.getStringProperty("contentType");
        Map<String, String> headers = new HashMap<String, String>();
        if (contentType != null) {
            headers.put("contentType", contentType);
        }

        try {
            String correlationId = message.getJMSCorrelationID();
            if (correlationId != null) {
                headers.put("correlationId", correlationId);
            }
        } catch (JMSException ignore) {
            // ignore
        }

        return new MqMessage(body, contentType, headers);
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (JMSException ignore) {
                // ignore
            }
        }
    }

    private static void closeQuietly(Session session) {
        if (session != null) {
            try {
                session.close();
            } catch (JMSException ignore) {
                // ignore
            }
        }
    }
}
