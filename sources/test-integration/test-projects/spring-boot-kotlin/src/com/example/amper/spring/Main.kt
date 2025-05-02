/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.amper.spring

import com.example.amper.spring.service.HelloService
import jakarta.annotation.PostConstruct
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication


@SpringBootApplication
class DemoApplication(private val helloService: HelloService) {
    @PostConstruct
    fun hello() {

        println(helloService.getHello())
    }
}

fun main(args: Array<String>) {
    runApplication<DemoApplication>()
}