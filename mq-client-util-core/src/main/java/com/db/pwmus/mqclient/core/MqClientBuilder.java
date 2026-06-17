package com.db.pwmus.mqclient.core;

/**
 * Builder for {@link MqClient} (constructs {@link DefaultMqClient}).
 */
public final class MqClientBuilder {
    private MqClientFactory factory;

    MqClientBuilder() {
    }

    public MqClientBuilder factory(MqClientFactory factory) {
        this.factory = factory;
        return this;
    }

    public MqClientBuilder classpathConfig(String resourceName) {
        this.factory = MqClientFactory.fromClasspath(resourceName);
        return this;
    }

    public MqClientBuilder fileConfig(String filePath) {
        this.factory = MqClientFactory.fromFilePath(filePath);
        return this;
    }

    public MqClient build() {
        if (factory == null) {
            throw new IllegalStateException("MqClientFactory is required (factory(), classpathConfig(), or fileConfig())");
        }
        return new DefaultMqClient(factory);
    }
}
