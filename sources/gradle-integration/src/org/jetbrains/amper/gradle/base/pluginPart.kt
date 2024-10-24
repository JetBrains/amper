/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle.base

import org.gradle.api.Project
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.gradle.ArtifactWrapper
import org.jetbrains.amper.gradle.PlatformAware
import org.jetbrains.amper.gradle.AmperModuleWrapper
import org.jetbrains.amper.gradle.moduleDir
import java.nio.file.Path

/**
 * Shared module plugin properties.
 */
interface BindingPluginPart {
    val project: Project
    val model: Model
    val module: AmperModuleWrapper
    val moduleToProject: Map<Path, String>

    val AmperModule.linkedProject
        get() = project.project(
            moduleToProject[moduleDir]
                ?: error("No linked Gradle project found for module $userReadableName")
        )

    val needToApply: Boolean get() = false

    /**
     * Logic, that needs to be applied before project evaluation.
     */
    fun applyBeforeEvaluate() {}

    /**
     * Logic, that needs to be applied after project evaluation.
     */
    fun applyAfterEvaluate() {}
}

open class NoneAwarePart(
    ctx: PluginPartCtx,
) : SpecificPlatformPluginPart(ctx, null), AmperNamingConventions

/**
 * Arguments deduplication class (by delegation in constructor).
 */
open class PluginPartCtx(
    override val project: Project,
    override val model: Model,
    override val module: AmperModuleWrapper,
    override val moduleToProject: Map<Path, String>,
) : BindingPluginPart

/**
 * Part with utilities to get specific artifacts and fragments.
 */
open class SpecificPlatformPluginPart(
    ctx: BindingPluginPart,
    private val platform: Platform?,
) : BindingPluginPart by ctx {

    @Suppress("LeakingThis")
    val platformArtifacts = module.artifacts.filterByPlatform(platform)

    val ArtifactWrapper.platformFragments get() = fragments.filterByPlatform(platform)

    @Suppress("LeakingThis")
    val platformFragments = module.fragments.filterByPlatform(platform)

    @Suppress("LeakingThis")
    val leafPlatformFragments = module.leafFragments.filterByPlatform(platform)

    private fun <T : PlatformAware> Collection<T>.filterByPlatform(platform: Platform?) =
        if (platform != null) filter { it.platforms.contains(platform) } else emptyList()

}
