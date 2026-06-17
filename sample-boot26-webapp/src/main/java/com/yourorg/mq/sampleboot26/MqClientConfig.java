package com.db.pwmus.mqclient.sampleboot26;

import com.db.pwmus.mqclient.core.MqClientFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MqClientConfig {
    @Bean(destroyMethod = "close")
    public MqClientFactory mqClientFactory() {
        return MqClientFactory.fromClasspath("mq-config.json");
    }
}

