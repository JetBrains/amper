/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.amper.springboot;


import com.example.amper.springboot.service.AmperService;
import jakarta.annotation.PostConstruct;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Main {

    private final AmperService amperService;

    public Main(AmperService amperService) {
        this.amperService = amperService;
    }

    public static void main(String[] args) {
        SpringApplication.run(Main.class, args);
    }

    @PostConstruct
    public void init() {
        System.out.println(amperService.getSampleMessage());
    }
}
