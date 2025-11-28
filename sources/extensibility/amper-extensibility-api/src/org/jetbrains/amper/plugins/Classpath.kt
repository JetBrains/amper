/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins

import java.nio.file.Path

/**
 * Use to get a resolved JVM classpath for the list of [dependencies].
 *
 * The resulting classpath can be obtained via [resolvedFiles] property.
 *
 * To conveniently get the classpath of the *current module* the plugin is applied to,
 * you can reference the provided values:
 * - `${module.runtimeClasspath}`
 * - `${module.compileClasspath}`
 */
@Configurable
interface Classpath {
    /**
     * Dependencies to resolve.
     * Version conflict resolution may apply if necessary for the given list of dependencies.
     *
     * @see Dependency.Local
     * @see Dependency.Maven
     */
    @Shorthand
    val dependencies: List<Dependency>

    /**
     * Resolution scope to use to resolve [dependencies].
     */
    val scope: ResolutionScope get() = ResolutionScope.Runtime

    /**
     * Resolved classpath files.
     */
    @Provided
    val resolvedFiles: List<Path>
}
