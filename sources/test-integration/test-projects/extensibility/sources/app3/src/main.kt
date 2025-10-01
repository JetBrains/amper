package com.example.amper.app

import java.util.ServiceLoader
import com.google.auto.service.AutoService

interface Greeter {
    fun greet(): String
}

@AutoService(Greeter::class)
class GreeterImpl : Greeter {
    override fun greet(): String = ""
}

fun main() { }