package com.toolize;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ToolizeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolizeApplication.class, args);
    }
}
