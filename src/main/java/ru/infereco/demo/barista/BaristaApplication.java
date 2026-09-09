package ru.infereco.demo.barista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
public class BaristaApplication {

    public static void main(String[] args) {
        System.setProperty("java.net.preferIPv4Addresses", "true");
        SpringApplication.run(BaristaApplication.class, args);
    }
}

