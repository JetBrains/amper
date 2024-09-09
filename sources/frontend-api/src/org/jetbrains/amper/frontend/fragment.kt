/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.Traceable
import org.jetbrains.amper.frontend.schema.Settings
import java.nio.file.Path
import kotlin.io.path.div


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
 * The path to the root of the all generated files for this [Fragment].
 *
 * This is guaranteed to be an ancestor of [getGeneratedResourcesRoot] and [getGeneratedSourcesRoot].
 */
fun Fragment.getGeneratedFilesRoot(buildOutputRoot: Path): Path =
    buildOutputRoot / "generated/${module.userReadableName}/$name"

/**
 * The path to the root of the generated sources for this [Fragment].
 *
 * The sources can be nested in different subdirectories for different generators, or different languages.
 * For example, KSP might store generated Kotlin sources under `<gen-src-root>/ksp/kotlin`, and generated Java sources
 * under `<gen-src-root>/ksp/java`.
 */
@UsedInIdePlugin
fun Fragment.getGeneratedSourcesRoot(buildOutputRoot: Path): Path =
    getGeneratedFilesRoot(buildOutputRoot) / "src"

/**
 * The path to the root of the generated resources for this [Fragment].
 *
 * The resources can be nested in different subdirectories for different generators, or different languages.
 * For example, KSP might store generated resources under `<gen-resources-root>/ksp`.
 */
@UsedInIdePlugin
fun Fragment.getGeneratedResourcesRoot(buildOutputRoot: Path): Path =
    getGeneratedFilesRoot(buildOutputRoot) / "resources"

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

private val Fragment.directModuleCompileDependencies: List<PotatoModule>
    get() = externalDependencies.filterIsInstance<PotatoModuleDependency>().filter { it.compile }.map { it.module }

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
            module.fragments.filter { it.platforms.containsAll(platforms) }
        }
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
