/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.schema.Module
import org.jetbrains.amper.frontend.schema.ProductType
import java.nio.file.Path

sealed interface PotatoModuleSource

object PotatoModuleProgrammaticSource : PotatoModuleSource

data class PotatoModuleFileSource(val buildFile: Path) : PotatoModuleSource {
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

/**
 * Just an aggregator for fragments and artifacts.
 */
interface PotatoModule {
    /**
     * To reference module somehow in output.
     */
    val userReadableName: String

    val type: ProductType

    val source: PotatoModuleSource

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

    val rootFragment: Fragment? get() = fragments.firstOrNull { it.fragmentDependencies.isEmpty() }

    val rootTestFragment: Fragment? get() = fragments.firstOrNull { it.isTest && it.fragmentDependencies.size == 1 }

}
