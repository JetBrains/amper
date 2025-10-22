package org.jetbrains.amper.plugins.hello

import org.jetbrains.amper.*

import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.readText
import kotlin.io.path.walk

@TaskAction
fun sayHello(
    messages: List<String>,
) {
    for (message in messages) {
        println(message)
    }
}

fun someFunction() {
    throw RuntimeException("Nested")
}

@TaskAction
fun crash() {
    try {
        someFunction()
    } catch (e: RuntimeException) {
        throw RuntimeException("Crashing on purpose", e)
    }
}