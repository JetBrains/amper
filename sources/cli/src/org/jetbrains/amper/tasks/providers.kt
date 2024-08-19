/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.Fragment
import java.nio.file.Path

/**
 * Provides additional JVM classes that should be considered as compile dependencies.
 */
internal interface AdditionalClasspathProvider {
    val classpath: List<Path>
}

/**
 * Provides additional source directories that should be considered for an existing fragment.
 */
internal interface AdditionalSourcesProvider {
    val sourceRoots: List<SourceRoot>

    data class SourceRoot(
        /** The name of the existing fragment that the sources should be associated to. */
        val fragmentName: String,
        /** The absolute path to the directory containing the sources. */
        val path: Path,
    ) {
        init {
            require(path.isAbsolute) { "Source root path should be absolute, got '$path'" }
        }
    }
}

/**
 * Provides additional resource directories that should be considered for an existing fragment.
 */
internal interface AdditionalResourcesProvider {
    val resourceRoots: List<ResourceRoot>

    data class ResourceRoot(
        /** The name of the existing fragment that the resources should be associated to. */
        val fragmentName: String,
        /** The absolute path to the directory containing the resources. */
        val path: Path,
    ) {
        init {
            require(path.isAbsolute) { "Resource root path should be absolute, got '$path'" }
        }
    }
}

/**
 * Get the sources from these [AdditionalSourcesProvider]s that correspond to the given [fragments].
 */
internal fun List<AdditionalSourcesProvider>.sourcesFor(fragments: Iterable<Fragment>): List<AdditionalSourcesProvider.SourceRoot> {
    val fragmentNames = fragments.mapTo(mutableSetOf()) { it.name }
    return flatMap { it.sourceRoots }.filter { it.fragmentName in fragmentNames }
}

/**
 * Get the resources from these [AdditionalResourcesProvider]s that correspond to the given [fragments].
 */
internal fun List<AdditionalResourcesProvider>.resourcesFor(fragments: Iterable<Fragment>): List<AdditionalResourcesProvider.ResourceRoot> {
    val fragmentNames = fragments.mapTo(mutableSetOf()) { it.name }
    return flatMap { it.resourceRoots }.filter { it.fragmentName in fragmentNames }
}
