/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package apkg

fun main() {
    val foo = foo {
        value = 42
    }
    
    println("Hello from the proto test! Request value is ${foo.value}")
}