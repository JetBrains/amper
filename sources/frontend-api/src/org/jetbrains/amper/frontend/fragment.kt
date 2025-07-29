/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.api.TraceableString
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
     * The modifier that can be used in the build file or in the source directories names to refer to this fragment.
     */
    val modifier: String

    /**
     * The module this fragment belongs to.
     */
    val module: AmperModule

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

    /**
     * Path to compose resources' directory.
     */
    val composeResourcesPath: Path

    /**
     * Whether any processable files are present in [composeResourcesPath].
     */
    val hasAnyComposeResources: Boolean

    /**
     * Paths to the generated source roots, relative to the build directory.
     */
    val generatedSrcRelativeDirs: List<Path>

    /**
     * Paths to the generated resource roots, relative to the build directory.
     */
    val generatedResourcesRelativeDirs: List<Path>

    /**
     * Paths to the generated classes roots, relative to the build directory.
     */
    val generatedClassesRelativeDirs: List<Path>

    val variants: List<String>
}

/**
 * Fragments (within the same module) that this fragment depends on with the FRIEND relationship.
 * Internal declarations from these fragments will be visible despite the independent compilation.
 */
val Fragment.friends: List<Fragment>
    get() = fragmentDependencies.filter { it.type == FragmentDependencyType.FRIEND }.map { it.target }

/**
 * Fragments (within the same module) that this fragment depends on with the REFINE relationship.
 * Expect declarations from these fragments will be visible despite a possible independent compilation (for instance,
 * this is useful for metadata compilation).
 */
val Fragment.refinedFragments: List<Fragment>
    get() = fragmentDependencies.filter { it.type == FragmentDependencyType.REFINE }.map { it.target }

/**
 * The fragment together with all fragments (within the same module) that this fragment depends on with the REFINE relationship transitively.
 * The fragment depends on all external dependencies declared for its complete refined fragments as well as on its own.
 */
val Fragment.withAllFragmentDependencies: Sequence<Fragment>
    get() = allFragmentDependencies(true)


private val Fragment.directModuleCompileDependencies: List<AmperModule>
    get() = externalDependencies.filterIsInstance<LocalModuleDependency>().filter { it.compile }.map { it.module }

/**
 * Source fragments (not Maven) that this fragment depends on with compile scope.
 *
 * This includes fragment dependencies from the same module, but also fragments from "external" source module
 * dependencies that support a superset of the platforms.
 *
 * For example, if the `nativeMain` fragment of Module A depends on Module B, [allSourceFragmentCompileDependencies]
 * contains the `commonMain` fragment of Module A, and the `nativeMain` and `commonMain` fragments of Module B.
 */
// TODO this will eventually be more complicated: we need to support other dimensions than target platforms
//  (some sort of attribute matching supporting variants and the likes).
//  It is worth sharing the complete algorithm with dependency resolution (the same thing will be implemented for
//  modules that are downloaded from Maven, using their metadata artifacts).
val Fragment.allSourceFragmentCompileDependencies: List<Fragment>
    get() {
        val fragmentsFromThisModule = fragmentDependencies.map { it.target }
        val fragmentsFromOtherModules = directModuleCompileDependencies.flatMap { module ->
            module.fragmentsTargeting(platforms, includeTestFragments = false)
        }

        // FIXME include transitive exported module dependencies

        return fragmentsFromThisModule + fragmentsFromOtherModules
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

interface LocalModuleDependency : DefaultScopedNotation {
    val module: AmperModule
}

sealed interface MavenDependencyBase : Notation {
    val coordinates: TraceableString
}

data class MavenDependency(
    override val coordinates: TraceableString,
    override val trace: Trace?,
    override val compile: Boolean = true,
    override val runtime: Boolean = true,
    override val exported: Boolean = false,
) : MavenDependencyBase, DefaultScopedNotation

data class BomDependency(
    override val coordinates: TraceableString,
    override val trace: Trace?,
) : MavenDependencyBase

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
