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
