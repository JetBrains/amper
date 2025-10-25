/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.bcv

import org.jetbrains.amper.plugins.Configurable

@Configurable
interface Settings {
    /**
     * Fully qualified package names that are not considered public API.
     * For example, it could be `kotlinx.coroutines.internal` or `kotlinx.serialization.implementation`.
     */
    val ignoredPackages: List<String> get() = emptyList()

    /**
     * Fully qualified names of annotations that effectively exclude declarations from being public.
     * Example of such annotation could be `kotlinx.coroutines.InternalCoroutinesApi`.
     */
    val nonPublicMarkers: List<String> get() = emptyList()

    /**
     * Fully qualified names of classes that are ignored by the API check.
     * Example of such a class could be `com.package.android.BuildConfig`.
     */
    val ignoredClasses: List<String> get() = emptyList()

    /**
     * Fully qualified names of annotations that can be used to explicitly mark public declarations.
     * If at least one of [publicMarkers], [publicPackages] or [publicClasses] is defined,
     * all declarations not covered by any of them will be considered non-public.
     * [ignoredPackages], [ignoredClasses] and [nonPublicMarkers] can be used for additional filtering.
     */
    val publicMarkers: List<String> get() = emptyList()

    /**
     * Fully qualified package names that contain public declarations.
     * If at least one of [publicMarkers], [publicPackages] or [publicClasses] is defined,
     * all declarations not covered by any of them will be considered non-public.
     * [ignoredPackages], [ignoredClasses] and [nonPublicMarkers] can be used for additional filtering.
     */
    val publicPackages: List<String> get() = emptyList()

    /**
     * Fully qualified names of public classes.
     * If at least one of [publicMarkers], [publicPackages] or [publicClasses] is defined,
     * all declarations not covered by any of them will be considered non-public.
     * [ignoredPackages], [ignoredClasses] and [nonPublicMarkers] can be used for additional filtering.
     */
    val publicClasses: List<String> get() = emptyList()

    /**
     * A path to a directory containing an API dump.
     * The path should be relative to the project's root directory and should resolve to its subdirectory.
     * By default, it's `api`.
     */
    val apiDumpDirectoryName: String get() = "api"
}