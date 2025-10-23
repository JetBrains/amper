/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

/**
 * @see ModuleSources
 */
enum class SourcesKind {
    /**
     * Kotlin + Java source directories, e.g., `src`, `src@jvm`, ...
     */
    KotlinJavaSources,

    /**
     * Java resources directories, e.g., `resources`, `resources@jvm`, ...
     */
    Resources,
}