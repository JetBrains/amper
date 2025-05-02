/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.amper.spring

import com.example.amper.spring.service.HelloService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import kotlin.test.Test

@SpringBootTest
class DemoApplicationTests {

    @Autowired
    lateinit var helloService: HelloService

    @Test
    fun contextLoads() {
        println(helloService.getHello())
    }
}