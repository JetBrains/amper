package com.example.demo;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@Slf4j
@SpringBootApplication
public class Demo18Application {

    public static void main(String[] args) {
        SpringApplication.run(Demo18Application.class, args);
    }

    @PostConstruct
    public void init() {
        log.info("post construct");
    }

}
