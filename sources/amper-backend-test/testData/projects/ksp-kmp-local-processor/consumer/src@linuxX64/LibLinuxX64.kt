package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import com.sample.myprocessor.gen.*

@MyKspAnnotation
class MyLinuxX64Class

fun useGeneratedLinuxX64Stuff() {
    MyCommonClassGenerated()
    MyLinuxX64ClassGenerated()
}
