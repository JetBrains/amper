/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.native

import java.nio.file.Path
import kotlin.io.path.extension

internal fun Iterable<Path>.filterKLibs() = filter {
    when(it.extension) {
        "klib" -> true
        "jar", "aar" -> false  // FIXME: AMPER-3862, should also be "not reached".
        else -> error("Unexpected file '${it.fileName}' in the native classpath. A bug in the DR?")
    }
}