/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable

/**
 * A dependency notation. It can have many different forms, defined by the subtypes of this interface.
 */
sealed interface Notation : Traceable

/**
 * A [Notation] for which the scope can be customized (compile-time, runtime, exporting to consumers).
 */
interface DefaultScopedNotation : Notation {
    val compile: Boolean get() = true
    val runtime: Boolean get() = true
    val exported: Boolean get() = false
}

/**
 * A [Notation] that represents a dependency on a local module within the project.
 */
interface LocalModuleDependency : DefaultScopedNotation {
    val module: AmperModule
}

/**
 * A [Notation] that describes an external Maven dependency via its coordinates..
 */
sealed interface MavenDependencyBase : Notation {
    val coordinates: MavenCoordinates
}

data class MavenCoordinates(
    val groupId: String,
    val artifactId: String,
    val version: String?,
    val classifier: String? = null,
    val packagingType: String? = null,
    override val trace: Trace,
) : Traceable {
    override fun toString() = buildString {
        append(groupId).append(':').append(artifactId)
        version?.let { append(':').append(it) }
        classifier?.let { append(':').append(it) }
        packagingType?.let { append('@').append(it) }
    }
}

data class MavenDependency(
    override val coordinates: MavenCoordinates,
    override val trace: Trace,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : MavenDependencyBase, DefaultScopedNotation

data class BomDependency(
    override val coordinates: MavenCoordinates,
    override val trace: Trace,
) : MavenDependencyBase
