/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import com.sample.myprocessor.gen.*
import kotlinx.cinterop.*
import platform.posix.*

@MyKspAnnotation
class MyLinuxX64Class

fun useGeneratedLinuxX64Stuff() {
    MyCommonClassGenerated()
    MyLinuxX64ClassGenerated()
}

@ExperimentalForeignApi
fun useNativeStdlibLinuxX64() {
    println("Hello from Native!")

    // some posix function
    time(null)
}
