package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project
import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.deft.proto.frontend.PotatoModule
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