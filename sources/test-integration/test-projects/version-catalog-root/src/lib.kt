/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import kotlinx.coroutines.delay

suspend fun foo() {
    delay(100)
    println("hello")
}
