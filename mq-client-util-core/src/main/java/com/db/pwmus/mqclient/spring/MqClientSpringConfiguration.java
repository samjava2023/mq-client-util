package com.db.pwmus.mqclient.spring;

import com.db.pwmus.mqclient.core.MqClient;
import com.db.pwmus.mqclient.core.MqClientFactory;
import com.db.pwmus.mqclient.listener.MqListenerRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqClientSpringConfiguration {

    @Bean(destroyMethod = "close")
    public MqClientFactory mqClientFactory(
            @Value("${mq.client.config-location:mq-config.json}") String configLocation) {
        return MqConfigLocationResolver.loadFactory(configLocation);
    }

    @Bean(destroyMethod = "close")
    public MqClient mqClient(MqClientFactory mqClientFactory) {
        return MqClient.builder().factory(mqClientFactory).build();
    }

    @Bean(destroyMethod = "close")
    public MqListenerRegistry mqListenerRegistry(MqClient mqClient) {
        return mqClient.listeners();
    }
}
