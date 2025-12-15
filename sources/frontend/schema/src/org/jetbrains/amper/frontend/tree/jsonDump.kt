/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree

import org.jetbrains.amper.frontend.contexts.Context
import org.jetbrains.amper.frontend.contexts.PathCtx
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.relativeTo


/**
 * Dumps the provided tree in JSON format.
 */
// TODO Rewrite with [JsomElement]s.
fun TreeNode.jsonDump(
    root: Path,
    contextsFilter: (Any) -> Boolean = { true },
): String {
    val normalizedRoot = root.absolute().normalize()
    fun Path.normalizedPath(): String = absolute().relativeTo(normalizedRoot).toString().replace('\\', '/')

    fun Context.pathCtxString() = if (this is PathCtx) path.toNioPath().normalizedPath() else null
    fun TreeNode.contextStr() = contexts
        .filter(contextsFilter).ifEmpty { null }
        ?.joinToString(separator = ",", prefix = "(", postfix = ")") { it.pathCtxString() ?: it.toString() }
        ?: ""

    // Hiding actual logic in internal function. Also, convenient for parameters passing.
    fun TreeNode.doJsonDump(indent: String, sb: Appendable): Appendable = sb.apply {
        fun <T> List<T>.dumpChildren(lb: String, rb: String, block: (T) -> Unit) = forEachIndexed { i, it ->
            if (i == 0) appendLine(lb)
            block(it)
            if (i != lastIndex) appendLine(",") else appendLine().append("$indent$rb")
        }

        val newIdent = "$indent  "
        when (this@doJsonDump) {
            is MappingNode -> children.dumpChildren("{", "}") {
                append("$newIdent\"${it.key}${it.value.contextStr()}\" : ")
                it.value.doJsonDump(newIdent, sb)
            }

            is ListNode -> children.dumpChildren("[", "]") {
                append(newIdent)
                it.doJsonDump(newIdent, sb)
            }

            is ErrorNode -> append("")

            is LeafTreeNode -> {
                val value = when (this@doJsonDump) {
                    is BooleanNode -> value.toString()
                    is EnumNode -> enumConstantIfAvailable?.toString() ?: entryName
                    is IntNode -> value.toString()
                    is PathNode -> value.normalizedPath()
                    is StringNode -> value
                    else -> "null"
                }
                append("\"$value${contextStr()}\"")
            }
        }
    }

    return doJsonDump("", StringBuilder()).toString()
}