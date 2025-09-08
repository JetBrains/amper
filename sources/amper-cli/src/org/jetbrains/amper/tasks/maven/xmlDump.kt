/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.maven

import org.jetbrains.amper.frontend.api.TraceablePath
import org.jetbrains.amper.frontend.tree.ListValue
import org.jetbrains.amper.frontend.tree.MapLikeValue
import org.jetbrains.amper.frontend.tree.ScalarValue
import org.jetbrains.amper.frontend.tree.TreeValue
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.relativeTo

/**
 * Dumps the provided tree in XML format for maven plugins usage.
 */
fun TreeValue<*>.mavenXmlDump(
    root: Path,
    initialIdent: String = "",
    propFilter: (MapLikeValue.Property<*>) -> Boolean = { true },
): String {
    val normalizedRoot = root.absolute().normalize()
    fun Path.normalizedPath(): String = absolute().relativeTo(normalizedRoot).toString().replace('\\', '/')
    val sb = StringBuilder()

    // Hiding actual logic in internal function. Also, convenient for parameters passing.
    fun TreeValue<*>.doXmlDump(indent: String, parentPropName: String? = null): Boolean {
        val newIdent = "$indent  "
        return when (this@doXmlDump) {
            is MapLikeValue -> {
                children.filter(propFilter).forEach {
                    sb.append("\n$newIdent<${it.key}>")
                    val withNewLine = it.value.doXmlDump(newIdent, it.key)
                    if (withNewLine) sb.append("/$newIdent\"</${it.key}>")
                    else sb.append("</${it.key}>")
                }
                true
            }

            is ListValue -> {
                // TODO Rework dump.
                // Ugly maven convention for naming list elements.
                val elementName = parentPropName?.removeSuffix("s") ?: return false
                children.forEach {
                    sb.append("\n$newIdent<$elementName>")
                    val withNewLine = it.doXmlDump(newIdent, null)
                    if (withNewLine) sb.append("/$newIdent\"</$elementName>")
                    else sb.append("</$elementName>")
                }
                children.isNotEmpty()
            }

            is ScalarValue -> {
                val asPath = (this@doXmlDump.value as? Path) ?: (value as? TraceablePath)?.value
                val asNormalizedPath = asPath?.normalizedPath()
                if (asNormalizedPath != null) sb.append(asNormalizedPath)
                else sb.append(value)
                false
            }

            else -> false
        }
    }

    return doXmlDump(initialIdent).toString()
}