/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import java.nio.file.Path

/**
 * TODO: docs
 */
@Schema
interface Classpath {
    @Shorthand
    val dependencies: List<Dependency>
    val scope: Scope get() = Scope.Runtime

    @Provided
    val resolvedFiles: List<Path>

    enum class Scope {
        Runtime,
        Compile,
    }
}