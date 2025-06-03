/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.cinterop.*
import platform.posix.*

fun main() {
    println("workingDir=${getCurrentWorkingDirectory()}")
}

@OptIn(ExperimentalForeignApi::class)
fun getCurrentWorkingDirectory(): String = ByteArray(PATH_MAX)
    .usePinned { getcwd(it.addressOf(0), PATH_MAX.convert()) ?: error("getcwd failed with errno $errno") }
    .toKString()
