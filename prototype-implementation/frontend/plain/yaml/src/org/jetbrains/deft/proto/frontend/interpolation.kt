package org.jetbrains.deft.proto.frontend

import java.io.InputStream
import java.util.*
import kotlin.io.path.Path


typealias InterpolateCtx = Properties

fun InputStream?.toInterpolateCtx() = Properties().apply {
    if (this@toInterpolateCtx != null)
        load(this@toInterpolateCtx)
}

context(InterpolateCtx)
fun String.tryInterpolate() = if (isSimpleVariable || isBracketSurroundedVariable) {
    val propName = removePrefix("$").removePrefix("{").removeSuffix("}")
    getProperty(propName) ?: error("No value for variable $propName")
} else {
    this
}

context(InterpolateCtx)
fun String.tryAbsolutePath() = if (startsWith(".")) {
    Path(this).toAbsolutePath().normalize().toString()
} else {
    this
}

private val String.isSimpleVariable: Boolean
    get() =
        startsWith("$") && !contains("\\{") && !contains("\\}")

private val String.isBracketSurroundedVariable: Boolean
    get() =
        startsWith("$\\{") && endsWith("}")

private fun String.toPropName(): String? =
    if (isSimpleVariable || isBracketSurroundedVariable)
        removePrefix("$").removePrefix("\\{").removeSuffix("}")
    else null