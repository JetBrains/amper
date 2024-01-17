/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.Result
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path


/**
 * Some part of the module that supports "single resolve context" invariant for
 * every source and resource file that is included.
 */
interface Fragment {
    val name: String

    val fragmentDependencies: List<FragmentLink>

    val fragmentDependants: List<FragmentLink>

    val externalDependencies: List<Notation>

    @Deprecated("Should be replaced with [settings]")
    val parts: ClassBasedSet<FragmentPart<*>>

    val settings: Settings

    val platforms: Set<Platform>

    val isTest: Boolean

    /**
     * Is this fragment is chosen by default when
     * no variants are specified?
     */
    val isDefault: Boolean

    /**
     * Path to the sources' directory.
     */
    val src: Path

    /**
     * Path to the resources' directory.
     */
    val resourcesPath: Path

    val variants: List<String>
}

/**
 * Leaf fragment must have only one platform.
 * Also, it should contain parts, that are specific
 * for concrete artifacts.
 *
 * Each result artifact is specified by single leaf fragment
 * (Except for KMP libraries).
 */
interface LeafFragment : Fragment {
    val platform: Platform
}

sealed interface Notation

interface DefaultScopedNotation : Notation {
    val compile: Boolean get() = true
    val runtime: Boolean get() = true
    val exported: Boolean get() = false
}

interface PotatoModuleDependency : DefaultScopedNotation {
    // A dirty hack to make module resolution lazy.
    context (ProblemReporterContext)
    val Model.module: Result<PotatoModule>
}

data class MavenDependency(
    val coordinates: String,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : DefaultScopedNotation

enum class FragmentDependencyType {
    REFINE, FRIEND,
}

/**
 * Dependency between fragments.
 * Can differ by type (refines dependency, test on sources dependency, etc.).
 */
interface FragmentLink {
    val target: Fragment
    val type: FragmentDependencyType
}

sealed interface FragmentPart<SelfT> {

    // Default propagation is overwriting.
    fun propagate(parent: SelfT): FragmentPart<*> = this
    fun default(module: PotatoModule): FragmentPart<*> = this
}
