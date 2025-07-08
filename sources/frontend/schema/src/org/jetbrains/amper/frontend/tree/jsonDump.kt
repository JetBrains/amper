/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.PathCtx
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.relativeTo


/**
 * Dumps the provided tree in JSON format.
 */
// TODO Rewrite with [JsomElement]s.
fun TreeValue<*>.jsonDump(
    root: Path,
    contextsFilter: (Any) -> Boolean = { true },
): String {
    val normalizedRoot = root.absolute().normalize()
    fun Path.normalizedPath(): String = absolute().relativeTo(normalizedRoot).toString().replace('\\', '/')

    fun Context.pathCtxString() = if (this is PathCtx) path.toNioPath().normalizedPath() else null
    fun TreeValue<*>.contextStr() = contexts
        .filter(contextsFilter).ifEmpty { null }
        ?.joinToString(",", "(", ")") { it.pathCtxString() ?: it.toString() }
        ?: ""

    // Hiding actual logic in internal function. Also, convenient for parameters passing.
    fun TreeValue<*>.doJsonDump(indent: String, sb: Appendable): Appendable = sb.apply {
        fun <T> List<T>.dumpChildren(lb: String, rb: String, block: (T) -> Unit) = forEachIndexed { i, it ->
            if (i == 0) appendLine(lb)
            block(it)
            if (i != lastIndex) appendLine(",") else appendLine().append("$indent$rb")
        }

        val newIdent = "$indent  "
        when (this@doJsonDump) {
            is MapLikeValue -> children.dumpChildren("{", "}") {
                append("$newIdent\"${it.key}${it.value.contextStr()}\" : ")
                it.value.doJsonDump(newIdent, sb)
            }

            is ListValue -> children.dumpChildren("[", "]") {
                append(newIdent)
                it.doJsonDump(newIdent, sb)
            }

            is ScalarOrReference -> {
                val asPath = (value as? Path) ?: (value as? TraceablePath)?.value
                val asNormalizedPath = asPath?.normalizedPath()
                if (asNormalizedPath != null) append("\"$asNormalizedPath${contextStr()}\"")
                else append("\"$value${contextStr()}\"")
            }

            is NoValue -> append("")
        }
    }

    return doJsonDump("", StringBuilder()).toString()
}