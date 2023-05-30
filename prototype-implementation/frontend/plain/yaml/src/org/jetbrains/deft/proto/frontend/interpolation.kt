package org.jetbrains.deft.proto.frontend

import java.io.InputStream
import java.util.Properties


typealias InterpolateCtx = Properties

fun InputStream?.toInterpolateCtx() = Properties().apply {
    if (this@toInterpolateCtx != null)
        load(this@toInterpolateCtx)
}

context(InterpolateCtx)
fun String.tryInterpolate(): String {
    val propName = removePrefix("$").removePrefix("{").removeSuffix("}")
    return getProperty(propName, this)
}