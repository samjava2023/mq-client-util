package com.db.pwmus.mqclient.spring;

import com.db.pwmus.mqclient.listener.MqListenerRegistry;
import com.db.pwmus.mqclient.listener.MqQueueMessageHandler;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Starts/stops background listeners declared in mq-config.json ({@code listen: true}).
 */
@Component
public class MqListenerLifecycle implements InitializingBean, DisposableBean {
    private final MqListenerRegistry listenerRegistry;
    private final List<MqQueueMessageHandler> queueHandlers;

    @Autowired
    public MqListenerLifecycle(MqListenerRegistry listenerRegistry,
                               @Autowired(required = false) List<MqQueueMessageHandler> queueHandlers) {
        this.listenerRegistry = listenerRegistry;
        this.queueHandlers = queueHandlers == null
            ? Collections.<MqQueueMessageHandler>emptyList()
            : queueHandlers;
    }

    @Override
    public void afterPropertiesSet() {
        listenerRegistry.registerAll(queueHandlers);
        listenerRegistry.startConfigured();
    }

    @Override
    public void destroy() {
        listenerRegistry.stopAll();
    }
}
