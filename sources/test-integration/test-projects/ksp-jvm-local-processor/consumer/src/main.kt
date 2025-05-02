package org.sample.ksp.localprocessor.consumer

import com.sample.ksp.localprocessor.annotation.MyKspAnnotation

fun main() {
    val resourceName = "/com/sample/generated/annotated-classes.txt"
    val resourceUrl = A::class.java.getResource(resourceName) ?: error( "Missing resource '$resourceName'")
    val text = resourceUrl.readText()
    println("My annotated classes are:")
    println(text)
}

@MyKspAnnotation
class A

@MyKspAnnotation
class B