package com.aicoding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AiCodingPlatformApplication {

    public static void main(String[] args) {
        SpringApplication.run(AiCodingPlatformApplication.class, args);
    }

}
