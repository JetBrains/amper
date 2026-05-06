/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.lazyload

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * This list comes from `amper-cli`'s `module.yaml`.
 */
internal enum class ExtraClasspath(val dirName: String) {
    PLUGINS_PROCESSOR(dirName = "plugins-processor"),
    EXTENSIBILITY_API(dirName = "extensibility-api"),
    RECOMPILER_EXTENSION(dirName = "recompiler-extension"),
    AMPER_JIC_RUNNER(dirName = "amper-jic-runner"),
    KOTLIN_BUILD_TOOLS_COMPAT(dirName = "kotlin-build-tools-compat");

    private val distRoot by lazy {
        Path(checkNotNull(System.getenv("AMPER_DISTRIBUTION_DIR")) {
            "Missing `AMPER_DISTRIBUTION_DIR` env var. Ensure your wrapper script integrity."
        })
    }

    /**
     * Returns the list of jars that belong to this [ExtraClasspath] from the Amper distribution.
     */
    fun findJarsInDistribution(): List<Path> = distRoot.resolve(dirName)
        .listDirectoryEntries("*.jar")
        .sortedWith(jarComparator)
}

private val jarComparator = Comparator<Path> { jar1, jar2 ->
    val jar1IsNaughty = jar1.isNaughtyJar()
    val jar2IsNaughty = jar2.isNaughtyJar()
    when {
        jar1IsNaughty && !jar2IsNaughty -> 1 // naughty jar1 should be considered "bigger" (last)
        !jar1IsNaughty && jar2IsNaughty -> -1 // naughty jar2 should be considered "bigger" (last)
        else -> jar1.name.compareTo(jar2.name) // both naughty or both good – order normally
    }
}

/**
 * These jars embed some non-shaded dependencies and, as such, will hijack the classloading of 3rd party classes
 * if they are in these dependencies. For example, kotlin-compiler-2.3.0.jar contains opentelemetry classes that
 * will be loaded instead of the correct ones if the kotlin-compiler jar is first in the classpath.
 */
private val prefixesOfNaughtyJarsThatShouldBeLast = listOf("kotlin-compiler-", "analysis-api-")

private fun Path.isNaughtyJar(): Boolean = prefixesOfNaughtyJarsThatShouldBeLast.any { name.startsWith(it) }