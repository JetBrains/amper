package com.sample.dagger

import dagger.*
import javax.inject.*

fun main() {
    // this is generated code that should be accessible after KSP runs
    DaggerCoffeeShop.create().maker().makeCoffee()
}

class Heater @Inject constructor() {
    fun heat() {
        println("Heater: heating...")
    }
}

class CoffeeMaker @Inject constructor(private val heater: Heater) {

    fun makeCoffee() {
        heater.heat()
        println("CoffeeMaker: brewing...")
    }
}

@Module
interface HeaterModule {

    companion object {
        @Provides
        fun provideHeater() = Heater()
    }
}

@Component(modules = [HeaterModule::class])
internal interface CoffeeShop {
    fun maker(): CoffeeMaker
}