package com.db.pwmus.mqclient.sampleboot14;

import com.db.pwmus.mqclient.spring.EnableMqClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMqClient
public class SampleBoot14Application {
    public static void main(String[] args) {
        SpringApplication.run(SampleBoot14Application.class, args);
    }
}

