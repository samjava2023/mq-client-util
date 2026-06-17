package com.db.pwmus.mqclient.sampleboot26;

import com.db.pwmus.mqclient.api.MqListener;
import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.api.MqMessageHandler;
import com.db.pwmus.mqclient.core.MqClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;

@Component
public class MqResponseListenerService {
    private static final Logger log = LoggerFactory.getLogger(MqResponseListenerService.class);

    private final MqClientFactory mqClientFactory;
    private final List<MqListener> backgroundListeners = new ArrayList<MqListener>();

    public MqResponseListenerService(MqClientFactory mqClientFactory) {
        this.mqClientFactory = mqClientFactory;
    }

    @PostConstruct
    public void startBackgroundListeners() {
        List<String> queueNames = mqClientFactory.getListenerQueueNames();
        if (queueNames.isEmpty()) {
            log.info("No background MQ listeners configured (set \"listen\": true on queues in mq-config.json)");
            return;
        }

        for (final String logicalQueue : queueNames) {
            MqListener listener = mqClientFactory.listener(logicalQueue);
            listener.onMessage(new MqMessageHandler() {
                @Override
                public void onMessage(MqMessage message) {
                    log.info("MQ message received from queue '{}': contentType={}, body={}",
                        logicalQueue,
                        message.getContentType(),
                        message.getBody());
                }
            });
            listener.start();
            backgroundListeners.add(listener);
            log.info("Background MQ listener started for logical queue '{}'", logicalQueue);
        }
    }

    @PreDestroy
    public void stopBackgroundListeners() {
        for (MqListener listener : backgroundListeners) {
            listener.stop();
        }
        backgroundListeners.clear();
        log.info("Background MQ listeners stopped");
    }
}
