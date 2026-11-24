package com.flagship.server;

import com.flagship.server.config.FlagshipServerProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(FlagshipServerProperties.class)
public class FlagshipServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlagshipServerApplication.class, args);
    }
}
