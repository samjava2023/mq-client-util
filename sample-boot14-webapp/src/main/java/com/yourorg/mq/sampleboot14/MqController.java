package com.db.pwmus.mqclient.sampleboot14;

import com.db.pwmus.mqclient.api.MqListener;
import com.db.pwmus.mqclient.api.MqMessage;
import com.db.pwmus.mqclient.api.MqSender;
import com.db.pwmus.mqclient.core.MqClientFactory;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

@RestController
public class MqController {
    private final MqClientFactory mq;

    public MqController(MqClientFactory mq) {
        this.mq = mq;
    }

    @RequestMapping(value = "/mq/sendJson", method = RequestMethod.GET)
    public String sendJson(@RequestParam("queue") String logicalQueue, @RequestParam("body") String body) {
        MqSender sender = mq.sender(logicalQueue);
        sender.sendJson(body);
        return "OK";
    }

    @RequestMapping(value = "/mq/sendJson", method = RequestMethod.POST)
    public String sendJsonPost(@RequestParam("queue") String logicalQueue, @RequestBody String body) {
        MqSender sender = mq.sender(logicalQueue);
        sender.sendJson(body);
        return "OK";
    }

    @RequestMapping(value = "/mq/sendXml", method = RequestMethod.POST)
    public String sendXmlPost(@RequestParam("queue") String logicalQueue, @RequestBody String body) {
        MqSender sender = mq.sender(logicalQueue);
        sender.sendXml(body);
        return "OK";
    }

    @RequestMapping(value = "/mq/receive", method = RequestMethod.GET)
    public String receive(@RequestParam("queue") String logicalQueue,
                          @RequestParam(value = "timeoutSec", required = false, defaultValue = "5") long timeoutSec) {
        MqListener listener = mq.listener(logicalQueue);
        MqMessage msg = listener.receive(timeoutSec, TimeUnit.SECONDS);
        return msg == null ? "" : msg.getBody();
    }
}

