package com.campusdesk;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CampusDeskApplication {
    public static void main(String[] args) {
        SpringApplication.run(CampusDeskApplication.class, args);
    }
}
