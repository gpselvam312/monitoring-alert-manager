package com.dxc.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MonitoringAlertManagerApplication {

    public static void main(String[] args) {
        SpringApplication.run(MonitoringAlertManagerApplication.class, args);
    }
}
