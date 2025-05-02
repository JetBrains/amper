package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import com.sample.myprocessor.gen.*

@MyKspAnnotation
class MyAndroidClass

fun useGeneratedAndroidStuff() {
    MyGeneratedClass()
    MyCommonClassGenerated()
    MyCommonClassGeneratedJava()
    MyAndroidClassGenerated()
    MyAndroidClassGeneratedJava()
}
