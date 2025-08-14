package com.sample.service.impl

import com.sample.service.MyService
import com.google.auto.service.AutoService

interface MyService {
    fun greet()
}

@AutoService(MyService::class)
class MyServiceImpl : MyService {

    override fun greet() {
        println("Hello, service!")
    }
}
