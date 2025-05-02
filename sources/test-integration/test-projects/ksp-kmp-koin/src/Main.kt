/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.sample.koin

import org.koin.core.annotation.*
import org.koin.core.component.*
import org.koin.core.context.startKoin
import org.koin.dsl.module

@Single
class Heater {
    fun heat() {
        println("Heater: heating...")
    }
}

@Single
class CoffeeMaker(private val heater: Heater) {
    fun makeCoffee() {
        heater.heat()
        println("CoffeeMaker: brewing...")
    }
}

@Module
@ComponentScan
class CoffeeShopModule

class CoffeeShop : KoinComponent {
    val coffeeMaker by inject<CoffeeMaker>()
}

fun main() {
    println( "Starting Koin...")
    // Just start Koin
    startKoin {
        printLogger()
        modules(CoffeeShopModule().module)
    }
    println( "Hello, Koin!")
    CoffeeShop().coffeeMaker.makeCoffee()
}
