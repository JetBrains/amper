/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import com.sample.myprocessor.gen.*

// has to be placed here because of how we collapse fragments
@MyKspAnnotation
class MyMingwX64Class

fun useGeneratedMingwX64Stuff() {
    MyCommonClassGenerated()
    MyMingwX64ClassGenerated()
}

fun useNativeStdlibMingwX64() {
    println("Hello from Native!")
}
