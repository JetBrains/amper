package org.jetbrains.deft.proto.gradle.base

import org.gradle.api.Project
import org.jetbrains.deft.proto.frontend.*
import org.jetbrains.deft.proto.gradle.*
import java.nio.file.Path

/**
 * Shared module plugin properties.
 */
interface BindingPluginPart {
    val project: Project
    val model: Model
    val module: PotatoModuleWrapper
    val moduleToProject: Map<Path, String>

    val PotatoModule.linkedProject
        get() = project.project(
                moduleToProject[buildDir]
                        ?: error("No linked Gradle project found for module $userReadableName")
        )

    // FIXME Rewrite path conventions completely!
    val Fragment.path get() = module.buildDir.resolve(name)
}

/**
 * Arguments deduplication class (by delegation in constructor).
 */
open class PluginPartCtx(
        override val project: Project,
        override val model: Model,
        override val module: PotatoModuleWrapper,
        override val moduleToProject: Map<Path, String>,
) : BindingPluginPart

/**
 * Part with utilities to get specific artifacts and fragments.
 */
open class SpecificPlatformPluginPart(
        ctx: BindingPluginPart,
        private val platform: Platform
) : BindingPluginPart by ctx {

    @Suppress("LeakingThis")
    private val platformArtifacts = module.artifacts.filterByPlatform(platform)

    private val platformNonTestArtifacts = platformArtifacts.filter { !it.isTest }

    private val platformTestArtifacts = platformArtifacts.filter { it.isTest }

    @Suppress("LeakingThis")
    val platformFragments = module.fragments.filterByPlatform(platform)

    internal val leafNonTestFragment = if (platformArtifacts.isEmpty()) null else {
        val artifact = platformNonTestArtifacts.requireSingle {
            "There must be exactly one non test ${platform.pretty} artifact!"
        }
        artifact.fragments.filterByPlatform(platform).requireSingle {
            "There must be only one non test ${platform.pretty} leaf fragment!"
        }
    }

    // TODO Can be redone if we will have multiple test artifacts.
    internal val leafTestFragment = if (platformArtifacts.isEmpty()) null else {
        val artifact = platformTestArtifacts.singleOrZero {
            error("There must be one or none test ${platform.pretty} artifact!")
        }
        artifact?.fragments?.filterByPlatform(platform)?.singleOrZero {
            error("There must be one or none test ${platform.pretty} leaf fragment!")
        }
    }

    private fun <T : PlatformAware> Collection<T>.filterByPlatform(platform: Platform) =
            filter { it.platforms.contains(platform) }

}