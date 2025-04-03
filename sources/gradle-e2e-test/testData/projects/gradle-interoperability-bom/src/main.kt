/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.slf4j.LoggerFactory

object MyClass

fun main() {
    LoggerFactory.getLogger(MyClass::class.java).info("Hello, world!")
}
