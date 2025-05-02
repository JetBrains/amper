/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.amper.spring.service

import org.springframework.stereotype.Service

@Service
class HelloService {

    fun getHello(): String {
        return "Hello World!"
    }
}