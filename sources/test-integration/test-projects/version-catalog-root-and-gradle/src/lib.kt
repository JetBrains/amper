/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
import kotlinx.coroutines.delay
import kotlinx.datetime.LocalTime

suspend fun foo() {
    delay(100)
    println(LocalTime(hour = 23, minute = 59, second = 12))
}
