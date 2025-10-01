/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

/**
 * @see Classpath
 */
enum class ResolutionScope {
    /**
     * `runtime` maven-like dependency scope.
     * Includes the dependencies that must be present in the runtime classpath.
     */
    Runtime,

    /**
     * `compile` maven-like dependency scope.
     * Includes the dependencies that must be present in the compilation classpath.
     */
    Compile,
}