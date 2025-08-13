/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.nio.file.Path
import kotlin.io.path.name
import kotlin.io.path.pathString

private val coroutinesDebugJarNameRegex = Regex("kotlinx-coroutines-debug-(?<version>.*)\\.jar")

internal fun List<Path>.coroutinesDebugVersion(): String =
    filter { "intellij" !in it.pathString } // to avoid intellij's fork of coroutines
        .firstNotNullOfOrNull { coroutinesDebugJarNameRegex.matchEntire(it.name) }
        ?.groups
        ?.get("version")
        ?.value
        ?: error("kotlinx-coroutines-debug jar not found in classpath")
