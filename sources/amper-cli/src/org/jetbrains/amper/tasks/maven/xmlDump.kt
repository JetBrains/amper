/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.jetbrains.amper.frontend.tree.BooleanNode
import org.jetbrains.amper.frontend.tree.CompleteListNode
import org.jetbrains.amper.frontend.tree.CompleteMappingNode
import org.jetbrains.amper.frontend.tree.CompleteObjectNode
import org.jetbrains.amper.frontend.tree.CompleteTreeNode
import org.jetbrains.amper.frontend.tree.EnumNode
import org.jetbrains.amper.frontend.tree.IntNode
import org.jetbrains.amper.frontend.tree.NullLiteralNode
import org.jetbrains.amper.frontend.tree.PathNode
import org.jetbrains.amper.frontend.tree.ScalarNode
import org.jetbrains.amper.frontend.tree.StringNode
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.relativeTo

/**
 * Dumps the provided tree in XML format for maven plugins usage.
 */
fun CompleteObjectNode.mavenXmlDump(
    root: Path,
    propFilter: (String) -> Boolean = { _ -> true },
): String {
    val normalizedRoot = root.absolute().normalize()
    fun Path.normalizedPath(): String = absolute().relativeTo(normalizedRoot).joinToString("/")
    val sb = StringBuilder()

    fun CompleteTreeNode.doXmlDump(indent: String, parentPropName: String? = null): Boolean {
        val newIdent = "$indent  "
        return when (this) {
            is CompleteMappingNode -> {
                for ((k, kv) in refinedChildren) {
                    if (kv.value is NullLiteralNode || !propFilter(k)) continue
                    sb.append("\n$indent<$k>")
                    val withNewLine = kv.value.doXmlDump(newIdent, k)
                    if (withNewLine) sb.append("\n$indent")
                    sb.append("</$k>")
                }
                true
            }
            is CompleteListNode -> {
                // TODO Rework dump.
                // Ugly maven convention for naming list elements.
                val elementName = parentPropName?.removeSuffix("s") ?: return false
                for (it in children) {
                    if (it is NullLiteralNode) continue
                    sb.append("\n$indent<$elementName>")
                    val withNewLine = it.doXmlDump(newIdent, null)
                    if (withNewLine) sb.append("\n$indent")
                    sb.append("</$elementName>")
                }
                children.isNotEmpty()
            }
            is NullLiteralNode -> false
            is ScalarNode -> {
                sb.append(when (this) {
                    is BooleanNode -> value
                    is EnumNode -> entryName
                    is IntNode -> value
                    is PathNode -> value.normalizedPath()
                    is StringNode -> value
                })
                false
            }
        }
    }

    doXmlDump("")
    return sb.toString().trim()
}