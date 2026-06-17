package com.db.pwmus.mqclient.listener;

import com.db.pwmus.mqclient.api.MqListener;
import com.db.pwmus.mqclient.api.MqMessageHandler;
import com.db.pwmus.mqclient.core.MqClientFactory;
import com.db.pwmus.mqclient.support.MqFlowLog;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages background listeners and their message-handler strategies.
 */
public final class MqListenerRegistry implements Closeable {
    private static final String CLASS = "MqListenerRegistry";

    private final MqClientFactory factory;
    private final Map<String, MqMessageHandler> handlers = new LinkedHashMap<String, MqMessageHandler>();
    private final List<MqListener> startedListeners = new ArrayList<MqListener>();

    public MqListenerRegistry(MqClientFactory factory) {
        this.factory = factory;
    }

    public void register(String logicalQueue, MqMessageHandler handler) {
        if (logicalQueue == null || logicalQueue.trim().isEmpty()) {
            throw new IllegalArgumentException("logicalQueue is required");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler is required");
        }
        handlers.put(logicalQueue, handler);
        MqFlowLog.info(CLASS, "register", "logicalQueue=" + logicalQueue
            + ", handler=" + handler.getClass().getName());
    }

    public void register(MqQueueMessageHandler handler) {
        register(handler.getLogicalQueueName(), handler);
    }

    public void registerAll(Iterable<MqQueueMessageHandler> queueHandlers) {
        if (queueHandlers == null) {
            return;
        }
        for (MqQueueMessageHandler handler : queueHandlers) {
            if (handler != null) {
                register(handler);
            }
        }
    }

    /**
     * Starts listeners for queues marked {@code listen: true} in mq-config.json.
     * Uses a registered handler when present, otherwise {@link LoggingMqMessageHandler}.
     */
    public synchronized void startConfigured() {
        List<String> queueNames = factory.getListenerQueueNames();
        if (queueNames.isEmpty()) {
            MqFlowLog.info(CLASS, "startConfigured", "no queues with listen=true");
            return;
        }
        for (String logicalQueue : queueNames) {
            MqMessageHandler handler = handlers.get(logicalQueue);
            if (handler == null) {
                handler = LoggingMqMessageHandler.forQueue(logicalQueue);
            }
            start(logicalQueue, handler);
        }
    }

    public synchronized void start(String logicalQueue, MqMessageHandler handler) {
        MqFlowLog.enter(CLASS, "start", "logicalQueue=" + logicalQueue);
        MqListener listener = factory.listener(logicalQueue);
        listener.onMessage(handler);
        listener.start();
        startedListeners.add(listener);
        MqFlowLog.success(CLASS, "start", "logicalQueue=" + logicalQueue);
    }

    public synchronized void stopAll() {
        MqFlowLog.enter(CLASS, "stopAll", "count=" + startedListeners.size());
        for (MqListener listener : startedListeners) {
            listener.stop();
        }
        startedListeners.clear();
        MqFlowLog.success(CLASS, "stopAll", "stopped");
    }

    public List<String> getRegisteredQueueNames() {
        return Collections.unmodifiableList(new ArrayList<String>(handlers.keySet()));
    }

    @Override
    public synchronized void close() {
        stopAll();
    }
}
