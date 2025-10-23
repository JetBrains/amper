/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import java.nio.file.Path

/**
 * Provides the compilation result of the [given][from] module.
 *
 * Warning: only JVM platform is currently supported.
 */
@Configurable
interface CompilationArtifact {
    /**
     * The local module to get the compilation result from.
     */
    val from: Dependency.Local

    /**
     * Path to the compilation artifact.
     * It's a JAR for JVM.
     */
    @Provided
    val artifact: Path
}