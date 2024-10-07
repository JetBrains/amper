/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.intellij.amper.lang.AmperFile
import com.intellij.psi.PsiFile
import org.jetbrains.yaml.psi.YAMLDocument
import org.jetbrains.yaml.psi.YAMLFile
import java.io.File
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.exists

context(Converter)
fun String.asAbsolutePath(): Path =
    this
        .replace("/", File.separator)
        .let {
            baseFile.toNioPath()
                .resolve(it)
                .absolute()
                .normalize()
                .apply {
                    // TODO Report non-existent paths.
                    if (!exists()) {

                    }
                }
        }

val PsiFile.topLevelValue get() = when (this) {
    is YAMLFile -> children.filterIsInstance<YAMLDocument>().firstOrNull()?.topLevelValue
    is AmperFile -> this
    else -> null
}

internal fun String.splitByCamelHumps(): String {
    val parts = mutableListOf<String>()
    var prevIndex = 0
    for ((index, letter) in withIndex()) {
        if (index > 0 && letter.isUpperCase()) {
            parts.add(substring(prevIndex, index))
            prevIndex = index
        }
    }
    parts.add(substring(prevIndex)) // last part
    return parts.joinToString(" ") { it.lowercase() }
}
