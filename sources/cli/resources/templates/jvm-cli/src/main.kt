/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.datetime.Clock

fun main() {
    println(Clock.System.now())
    println("Hello, ${World.get()}!")
}
