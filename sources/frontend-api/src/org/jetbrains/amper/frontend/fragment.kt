/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path


/**
 * Some part of the module that supports "single resolve context" invariant for
 * every source and resource file that is included.
 */
interface Fragment {
    /**
     * The name of this fragment.
     */
    val name: String

    /**
     * The module this fragment belongs to.
     */
    val module: PotatoModule

    /**
     * Fragments (within the same module) that this fragment depends on.
     */
    val fragmentDependencies: List<FragmentLink>

    /**
     * Fragments (within the same module) that depend on this fragment.
     */
    val fragmentDependants: List<FragmentLink>

    /**
     * Dependencies of this fragment. They can be Maven modules or other source modules in the project.
     */
    val externalDependencies: List<Notation>

    /**
     * The settings of this fragment, including inherited settings from parent fragments.
     * For instance, the settings of the iosArm64 fragment contain merged settings from iosArm64, ios, native, and common.
     */
    val settings: Settings

    /**
     * Leaf platforms that this fragment is compiled to.
     */
    val platforms: Set<Platform>

    /**
     * Whether this fragment contains test-only sources.
     */
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

sealed interface Notation : Traceable

interface DefaultScopedNotation : Notation {
    val compile: Boolean get() = true
    val runtime: Boolean get() = true
    val exported: Boolean get() = false
}

interface PotatoModuleDependency : DefaultScopedNotation {
    val module: PotatoModule
}

data class MavenDependency(
    val coordinates: String,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : DefaultScopedNotation, Traceable {
    override var trace: Trace? = null
}

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
