package com.db.pwmus.mqclient.sampleboot26;

import com.db.pwmus.mqclient.spring.EnableMqClient;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableMqClient
public class SampleBoot26Application {
    public static void main(String[] args) {
        SpringApplication.run(SampleBoot26Application.class, args);
    }
}

