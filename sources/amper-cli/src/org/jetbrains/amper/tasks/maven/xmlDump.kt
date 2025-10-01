/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.TraceablePath
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.relativeTo

/**
 * Dumps the provided tree in XML format for maven plugins usage.
 */
fun SchemaNode.mavenXmlDump(
    root: Path,
    propFilter: (String, Any?) -> Boolean = { _, _, -> true },
): String {
    val normalizedRoot = root.absolute().normalize()
    fun Path.normalizedPath(): String = absolute().relativeTo(normalizedRoot).toString().replace('\\', '/')
    val sb = StringBuilder()

    // Hiding actual logic in internal function. Also, convenient for parameters passing.
    fun Any.doXmlDump(indent: String, parentPropName: String? = null): Boolean {
        val newIdent = "$indent  "
        return when (this@doXmlDump) {
            is SchemaNode -> {
                valueHolders.filter { propFilter(it.key, it.value.value) }.forEach {
                    sb.append("\n$newIdent<${it.key}>")
                    val withNewLine = it.value.doXmlDump(newIdent, it.key)
                    if (withNewLine) sb.append(newIdent)
                    sb.append("</${it.key}>")
                }
                true
            }

            is Map<*, *> -> {
                this.filter { propFilter(it.key?.toString() ?: return@filter false, it.value) }.forEach {
                    sb.append("\n$newIdent<${it.key}>")
                    val withNewLine = it.value?.doXmlDump(newIdent, it.key.toString()) ?: false
                    if (withNewLine) sb.append(newIdent)
                    sb.append("</${it.key}>")
                }
                true
            }

            is List<*> -> {
                // TODO Rework dump.
                // Ugly maven convention for naming list elements.
                val elementName = parentPropName?.removeSuffix("s") ?: return false
                forEach {
                    sb.append("\n$newIdent<$elementName>")
                    val withNewLine = it?.doXmlDump(newIdent, null) ?: false
                    if (withNewLine) sb.append(newIdent)
                    sb.append("</$elementName>")
                }
                isNotEmpty()
            }

            else -> {
                val asPath = (this as? Path) ?: (this as? TraceablePath)?.value
                val asNormalizedPath = asPath?.normalizedPath()
                if (asNormalizedPath != null) sb.append(asNormalizedPath)
                else sb.append(this)
                false
            }
        }
    }

    return doXmlDump("").toString()
}