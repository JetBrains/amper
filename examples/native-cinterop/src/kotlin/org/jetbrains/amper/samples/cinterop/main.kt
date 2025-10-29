package org.jetbrains.amper.samples.cinterop

import kotlinx.cinterop.*
import hello.*

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun main() = memScoped {
    println("Hello from Kotlin!")
    val name = "Supremo"
    sayHello(name)
}