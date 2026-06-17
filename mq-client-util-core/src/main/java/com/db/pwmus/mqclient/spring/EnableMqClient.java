package com.db.pwmus.mqclient.spring;

import org.springframework.context.annotation.Import;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Enables MQ client beans and background listeners.
 * <p>
 * Add to your {@code @SpringBootApplication} class. Configure with
 * {@code mq.client.config-location=mq-config.json} in application.properties.
 * REST endpoints are not included — add your own controller in the application.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Import({
    MqClientSpringConfiguration.class,
    MqListenerLifecycle.class
})
public @interface EnableMqClient {
}
