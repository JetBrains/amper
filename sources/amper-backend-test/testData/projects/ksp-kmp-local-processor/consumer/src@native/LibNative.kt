package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import kotlinx.cinterop.*
import kotlin.experimental.*
import kotlin.native.concurrent.*

@MyKspAnnotation
class MyNativeClass

@ExperimentalForeignApi
fun useNativeStdlib() {
    println("Hello from Native!")

    // some cinterop function
    memScoped { 42 }
}

@OptIn(ExperimentalNativeApi::class)
fun declarationWithNativeAPI(): CpuArchitecture = TODO()