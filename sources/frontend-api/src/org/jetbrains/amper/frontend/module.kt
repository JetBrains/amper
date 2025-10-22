/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.plugins.AmperMavenPluginDescription
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.frontend.schema.PluginSettings
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Repository.Companion.SpecialMavenLocalUrl
import java.nio.file.Path

data class AmperModuleFileSource(val buildFile: Path) {
    /**
     * The directory containing the `module.yaml` or Gradle build file of the module.
     */
    val moduleDir: Path
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
     * Maven-like mode. Main and test sources are located inside src/ and sources are split by type (language, purpose,
     * etc.). The Gradle `java` plugin also uses this layout. It helps to simplify the transition between Amper and 
     * Maven/Gradle builds.
     *
     * Example:
     *
     * src/
     *   main/
     *     java/
     *     kotlin/
     *     resources/
     *   test/
     *     java/
     *     kotlin/
     *     resources/
     */
    MAVEN_LIKE,

    /**
     * Mode, when `src` and `src@jvm` like platform
     * specific directories layout are used.
     * Non-Amper source sets have no directories at all.
     */
    AMPER,
}

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

    val source: AmperModuleFileSource

    /**
     * The platform aliases defined in this module.
     */
    val aliases: Map<String, Set<Platform>>

    /**
     * List of all the fragments in the module. Can be empty if no platforms were specified.
     */
    val fragments: List<Fragment>

    val artifacts: List<Artifact>

    val parts: ClassBasedSet<ModulePart<*>>

    @UsedInIdePlugin
    val usedCatalog: VersionCatalog

    @UsedInIdePlugin
    val usedTemplates: List<VirtualFile>
    
    val leafFragments get() = fragments.filterIsInstance<LeafFragment>()

    val leafPlatforms: Set<Platform> get() = leafFragments.map { it.platform }.toSet()

    val tasksFromPlugins: List<TaskFromPluginDescription>

    val layout: Layout

    val amperMavenPluginsDescriptions: List<AmperMavenPluginDescription>
    
    val pluginSettings: PluginSettings
}

/**
 * Returns all fragments in this module that target at least the given set of [platforms].
 * If [includeTestFragments] is false, only production fragments are returned.
 */
fun AmperModule.fragmentsTargeting(platforms: Set<Platform>, includeTestFragments: Boolean): List<Fragment> =
    fragments.filter { (includeTestFragments || !it.isTest) && it.platforms.containsAll(platforms) }

fun AmperModule.fragmentsTargeting(platform: Platform, includeTestFragments: Boolean): List<Fragment> =
    fragmentsTargeting(setOf(platform), includeTestFragments)

/**
 * Returns whether maven publishing is enabled for this module.
 */
// We don't have to go through all fragments, the InconsistentPublishingSettings factory already checked
// that all fragments have the same publishing settings.
fun AmperModule.hasPublishingConfigured() = fragments.first().settings.publishing.enabled
