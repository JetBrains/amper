/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.github.ajalt.clikt.parameters.options.OptionWithValues
import com.github.ajalt.clikt.parameters.options.convert
import java.io.File
import java.nio.file.Path
import kotlin.io.path.Path

fun OptionWithValues<String?, String, String>.classpath() = convert { it.parseClasspath() }

fun OptionWithValues<String?, String, String>.namedClasspath() = convert {
    NamedClasspath(
        name = it.substringBefore('='),
        classpath = it.substringAfter('=').parseClasspath(),
    )
}

data class NamedClasspath(val name: String, val classpath: List<Path>)

private fun String.parseClasspath(): List<Path> = split(File.pathSeparator).map { Path(it) }
