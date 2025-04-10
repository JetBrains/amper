/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import java.nio.file.Path

@Deprecated(
    message = "PotatoModuleSource was renamed to AmperModuleSource",
    replaceWith = ReplaceWith("AmperModuleSource", imports = [ "org.jetbrains.amper.frontend.AmperModuleSource" ]),
)
@UsedInIdePlugin
typealias PotatoModuleSource = AmperModuleSource

sealed interface AmperModuleSource {
    /**
     * The directory containing the `module.yaml` or Gradle build file of the module.
     * May be null for unresolved modules or programmatically generated modules.
     */
    val moduleDir: Path?
}

@Deprecated(
    message = "PotatoModuleProgrammaticSource was renamed to AmperModuleProgrammaticSource",
    replaceWith = ReplaceWith("AmperModuleProgrammaticSource", imports = [ "org.jetbrains.amper.frontend.AmperModuleProgrammaticSource" ]),
)
@UsedInIdePlugin
typealias PotatoModuleProgrammaticSource = AmperModuleProgrammaticSource

open class AmperModuleProgrammaticSource : AmperModuleSource {
    override val moduleDir: Nothing? = null

    companion object : AmperModuleProgrammaticSource()
}

data class AmperModuleInvalidPathSource(
    val invalidPath: Path,
) : AmperModuleProgrammaticSource()

@Deprecated(
    message = "PotatoModuleFileSource was renamed to AmperModuleFileSource",
    replaceWith = ReplaceWith(
        expression = "AmperModuleFileSource",
        imports = ["org.jetbrains.amper.frontend.AmperModuleFileSource"]
    ),
)
@UsedInIdePlugin
typealias PotatoModuleFileSource = AmperModuleFileSource

data class AmperModuleFileSource(val buildFile: Path) : AmperModuleSource {
    /**
     * The directory containing the `module.yaml` or Gradle build file of the module.
     */
    override val moduleDir: Path
        get() = buildFile.parent
}

sealed interface ModulePart<SelfT>

data class RepositoriesModulePart(
    val mavenRepositories: List<Repository>
) : ModulePart<RepositoriesModulePart> {
    data class Repository(
        val id: String,
        val url: String,
        val publish: Boolean = false,
        val resolve: Boolean = true,
        val userName: String? = null,
        val password: String? = null,
    )
}

data class ModuleTasksPart(
    val settings: Map<String, TaskSettings>,
) : ModulePart<ModuleTasksPart> {
    data class TaskSettings(val dependsOn: List<String>)
}

enum class Layout {
    /**
     * Mode, when Gradle kotlin source sets layout is preserved.
     * `commonMain` directory is renamed to `common`.
     */
    GRADLE,

    /**
     * Mode, like [GRADLE], except that `jvm` source set is
     * renamed by `main` to be compatible with kotlin("jvm")
     */
    GRADLE_JVM,

    /**
     * Mode, when `src` and `src@jvm` like platform
     * specific directories layout are used.
     * Non-Amper source sets have no directories at all.
     */
    AMPER,
}

data class MetaModulePart(
    val layout: Layout = Layout.AMPER
) : ModulePart<MetaModulePart>

@Deprecated(
    message = "PotatoModule was renamed to AmperModule",
    replaceWith = ReplaceWith("AmperModule", imports = [ "org.jetbrains.amper.frontend.AmperModule" ]),
)
@UsedInIdePlugin
typealias PotatoModule = AmperModule

/**
 * Just an aggregator for fragments and artifacts.
 */
interface AmperModule {
    /**
     * To reference module somehow in output.
     */
    val userReadableName: String

    val type: ProductType

    val source: AmperModuleSource

    /**
     * Original schema values, that this module came from.
     */
    val origin: Module

    val fragments: List<Fragment>

    val artifacts: List<Artifact>

    val parts: ClassBasedSet<ModulePart<*>>

    @UsedInIdePlugin
    val usedCatalog: VersionCatalog?

    val leafFragments get() = fragments.filterIsInstance<LeafFragment>()

    val rootFragment: Fragment get() = fragments.first { it.fragmentDependencies.isEmpty() }

    val rootTestFragment: Fragment get() = fragments.first { it.isTest && it.fragmentDependencies.size == 1 }

    val leafPlatforms: Set<Platform> get() = leafFragments.map { it.platform }.toSet()

    val customTasks: List<CustomTaskDescription>
}

/**
 * Returns all fragments in this module that target the given [platforms].
 * If [includeTestFragments] is false, only production fragments are returned.
 */
fun AmperModule.fragmentsTargeting(platforms: Set<Platform>, includeTestFragments: Boolean): List<Fragment> =
    fragments.filter { (includeTestFragments || !it.isTest) && it.platforms.containsAll(platforms) }

fun AmperModule.fragmentsTargeting(platform: Platform, includeTestFragments: Boolean): List<Fragment> =
    fragmentsTargeting(setOf(platform), includeTestFragments)
