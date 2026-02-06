package org.jetbrains.amper.samples.cinterop

import kotlinx.cinterop.*
import org.jetbrains.amper.samples.cinterop.hello.* // Import the generated bindings

fun main() {
    println("Hello, John Doe from Kotlin!")
    sayHello("John Doe")
}