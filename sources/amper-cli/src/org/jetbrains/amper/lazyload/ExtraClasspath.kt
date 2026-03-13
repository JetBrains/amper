/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.lazyload

import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.listDirectoryEntries

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
        Path(checkNotNull(System.getProperty("amper.dist.path")) {
            "Missing `amper.dist.path` system property. Ensure your wrapper script integrity."
        })
    }

    /**
     * Returns the list of jars that belong to this [ExtraClasspath] from the Amper distribution.
     */
    fun findJarsInDistribution(): List<Path> = distRoot.resolve(dirName).listDirectoryEntries("*.jar")
}
