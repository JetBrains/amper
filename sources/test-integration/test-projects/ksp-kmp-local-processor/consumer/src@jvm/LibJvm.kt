package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import com.sample.myprocessor.gen.*

@MyKspAnnotation
class MyJvmClass

fun useGeneratedJvmStuff() {
    MyGeneratedClass()
    MyCommonClassGenerated()
    MyCommonClassGeneratedJava()
    MyJvmClassGenerated()
    MyJvmClassGeneratedJava()
}
