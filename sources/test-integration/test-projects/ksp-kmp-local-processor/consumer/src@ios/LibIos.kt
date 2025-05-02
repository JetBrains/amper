package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import platform.UIKit.*

@MyKspAnnotation
class MyIosClass

fun useNativeStdlibIos() {
    println("Hello from Native!")
}

// Available only on iOS and tvOS
fun createView(): UIView = UIView()
