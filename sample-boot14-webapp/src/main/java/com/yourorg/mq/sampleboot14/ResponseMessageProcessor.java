package com.db.pwmus.mqclient.sampleboot14;

import com.db.pwmus.mqclient.api.MqMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Placeholder for business-specific handling of messages from {@code responseQ}.
 * Replace or extend this service with your domain logic.
 */
@Service
public class ResponseMessageProcessor {
    private static final Logger log = LoggerFactory.getLogger(ResponseMessageProcessor.class);

    public void process(MqMessage message) {
        String contentType = message.getContentType();
        String body = message.getBody();

        if (body == null || body.trim().isEmpty()) {
            log.warn("Empty message body — skipping business processing");
            return;
        }

        if ("application/json".equalsIgnoreCase(contentType)
            || (contentType == null && body.trim().startsWith("{"))) {
            handleJsonResponse(body, message);
            return;
        }

        if ("application/xml".equalsIgnoreCase(contentType)
            || (contentType == null && body.trim().startsWith("<"))) {
            handleXmlResponse(body, message);
            return;
        }

        handlePlainTextResponse(body, message);
    }

    private void handleJsonResponse(String body, MqMessage message) {
        log.info("Business task [JSON]: parse payload and update downstream systems");
        log.debug("JSON payload: {}", body);
        // e.g. ObjectMapper.readValue(body, MyDto.class)
    }

    private void handleXmlResponse(String body, MqMessage message) {
        log.info("Business task [XML]: parse payload and route to workflow");
        log.debug("XML payload: {}", body);
        // e.g. JAXB / DOM parse, correlationId from message.getHeaders()
        String correlationId = message.getHeaders().get("correlationId");
        if (correlationId != null) {
            log.info("correlationId from headers: {}", correlationId);
        }
    }

    private void handlePlainTextResponse(String body, MqMessage message) {
        log.info("Business task [text]: body length={}", body.length());
    }
}
