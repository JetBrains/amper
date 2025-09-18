/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper

import java.nio.file.Path

/**
 * Use to get module [source directories][sourceDirectories] from the module.
 * Takes the source layout option into account.
 *
 * Currently, only JVM non-test sources are supported.
 */
@Schema
interface ModuleSources {
    /**
     * Module to get source directories for.
     */
    val from: Dependency.Local

    /**
     * Kotlin/Java source directories for the [module][from].
     * There can be multiple source directories for the module.
     * Not all of them may exist.
     */
    @Provided
    val sourceDirectories: List<Path>
}
