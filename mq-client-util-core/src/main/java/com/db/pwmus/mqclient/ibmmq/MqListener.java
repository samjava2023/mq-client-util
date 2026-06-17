package com.db.pwmus.mqclient.ibmmq;

import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.api.MqMessageHandler;
import com.db.pwmus.mqclient.config.ConnectionConfig;
import com.db.pwmus.mqclient.config.QueueConfig;
import com.db.pwmus.mqclient.support.MqFlowLog;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

final class MqListener implements com.db.pwmus.mqclient.api.MqListener {
    private static final String CLASS = "MqListener";
    private static final long POLL_TIMEOUT_SECONDS = 5L;

    private final ConnectionConfig connectionConfig;
    private final QueueConfig queueConfig;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile MqMessageHandler handler;
    private Thread worker;

    MqListener(ConnectionConfig connectionConfig, QueueConfig queueConfig) {
        this.connectionConfig = connectionConfig;
        this.queueConfig = queueConfig;
    }

    @Override
    public void onMessage(MqMessageHandler handler) {
        MqFlowLog.info(CLASS, "onMessage", "physicalQueue=" + queueConfig.getResolvedName()
            + ", handler=" + (handler == null ? "null" : handler.getClass().getName()));
        this.handler = handler;
    }

    @Override
    public synchronized void start() {
        MqFlowLog.enter(CLASS, "start", "physicalQueue=" + queueConfig.getResolvedName()
            + " | " + MqFlowLog.formatConnection(null, connectionConfig)
            + " | " + MqFlowLog.formatQueue(null, queueConfig));
        if (running.get()) {
            MqFlowLog.info(CLASS, "start", "already running, physicalQueue=" + queueConfig.getResolvedName());
            return;
        }
        if (handler == null) {
            throw new IllegalStateException("Call onMessage(handler) before start()");
        }
        running.set(true);
        worker = new Thread(new PollingRunnable(), "ibm-mq-listener-" + queueConfig.getResolvedName());
        worker.setDaemon(true);
        worker.start();
        MqFlowLog.success(CLASS, "start", "physicalQueue=" + queueConfig.getResolvedName()
            + ", pollTimeoutSec=" + POLL_TIMEOUT_SECONDS);
    }

    @Override
    public synchronized void stop() {
        MqFlowLog.enter(CLASS, "stop", "physicalQueue=" + queueConfig.getResolvedName());
        running.set(false);
        if (worker != null) {
            worker.interrupt();
            try {
                worker.join(3000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
        MqFlowLog.success(CLASS, "stop", "physicalQueue=" + queueConfig.getResolvedName());
    }

    @Override
    public MqMessage receive(long timeout, TimeUnit unit) {
        MqFlowLog.enter(CLASS, "receive", "physicalQueue=" + queueConfig.getResolvedName()
            + ", timeout=" + timeout + ", unit=" + unit
            + " | " + MqFlowLog.formatConnection(null, connectionConfig));
        try {
            MqMessage message = IbmMqJmsSupport.receive(connectionConfig, queueConfig, timeout, unit);
            if (message == null) {
                MqFlowLog.info(CLASS, "receive", "no message, physicalQueue=" + queueConfig.getResolvedName());
            } else {
                MqFlowLog.success(CLASS, "receive", "physicalQueue=" + queueConfig.getResolvedName()
                    + ", contentType=" + message.getContentType()
                    + ", bodyLength=" + (message.getBody() == null ? 0 : message.getBody().length())
                    + ", body=" + MqFlowLog.formatBody(message.getBody())
                    + ", headers=" + MqFlowLog.formatHeaders(message.getHeaders()));
            }
            return message;
        } catch (RuntimeException e) {
            MqFlowLog.fail(CLASS, "receive", "physicalQueue=" + queueConfig.getResolvedName(), e);
            throw e;
        }
    }

    @Override
    public MqMessage receiveWhere(String correlationId, long timeout, TimeUnit unit) {
        throw new UnsupportedOperationException("receiveWhere not implemented yet");
    }

    private final class PollingRunnable implements Runnable {
        @Override
        public void run() {
            while (running.get()) {
                try {
                    MqMessage message = IbmMqJmsSupport.receive(
                        connectionConfig,
                        queueConfig,
                        POLL_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                    );
                    if (message != null && handler != null) {
                        MqFlowLog.info(CLASS, "onMessage.dispatch", "physicalQueue=" + queueConfig.getResolvedName()
                            + ", contentType=" + message.getContentType()
                            + ", bodyLength=" + (message.getBody() == null ? 0 : message.getBody().length())
                            + ", body=" + MqFlowLog.formatBody(message.getBody())
                            + ", handler=" + handler.getClass().getName());
                        handler.onMessage(message);
                    }
                } catch (Exception e) {
                    if (running.get()) {
                        MqFlowLog.fail(CLASS, "poll", "physicalQueue=" + queueConfig.getResolvedName(), e);
                        sleepQuietly(2000L);
                    }
                }
            }
        }

        private void sleepQuietly(long millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
