/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Repository.Companion.SpecialMavenLocalUrl
import java.nio.file.Path

sealed interface AmperModuleSource {
    /**
     * The directory containing the `module.yaml` or Gradle build file of the module.
     * May be null for unresolved modules or programmatically generated modules.
     */
    val moduleDir: Path?
}

open class AmperModuleProgrammaticSource : AmperModuleSource {
    override val moduleDir: Nothing? = null

    companion object : AmperModuleProgrammaticSource()
}

data class AmperModuleInvalidPathSource(
    val invalidPath: Path,
) : AmperModuleProgrammaticSource()

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
    ) {
        val isMavenLocal = url == SpecialMavenLocalUrl
    }
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

/**
 * Just an aggregator for fragments and artifacts.
 */
// TODO Add trace.
interface AmperModule {
    /**
     * To reference module somehow in output.
     */
    val userReadableName: String

    val type: ProductType

    val source: AmperModuleSource

    val fragments: List<Fragment>

    val artifacts: List<Artifact>

    val parts: ClassBasedSet<ModulePart<*>>

    @UsedInIdePlugin
    val usedCatalog: VersionCatalog?

    @UsedInIdePlugin
    val usedTemplates: List<VirtualFile>
    
    val leafFragments get() = fragments.filterIsInstance<LeafFragment>()

    val rootFragment: Fragment get() = fragments.first { it.fragmentDependencies.isEmpty() }

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
