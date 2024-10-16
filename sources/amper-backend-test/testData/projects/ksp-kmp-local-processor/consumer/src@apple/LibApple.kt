package com.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation
import platform.darwin.*
import platform.Foundation.*

@MyKspAnnotation
class MyAppleClass

// Available only on Apple platforms
fun declarationWithDarwinAPI(): NSURL = TODO()

// Available only on Apple platforms
fun declarationWithFoundationAPI(): NSObject = TODO()
