package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.Model
import org.gradle.api.Project
import org.jetbrains.deft.proto.frontend.PotatoModule
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.nio.file.Path

/**
 * Shared module plugin properties.
 */
interface BindingPluginPart {
    val project: Project
    val model: Model
    val module: PotatoModuleWrapper
    val moduleToProject: Map<Path, String>

    fun KotlinProjectExtension.applyForKotlinSourceSet(sourceSetName: String, action: KotlinSourceSet.() -> Unit) =
        getOrCreateKotlinSourceSet(sourceSetName).action()

    fun KotlinProjectExtension.getOrCreateKotlinSourceSet(sourceSetName: String): KotlinSourceSet =
        sourceSets.maybeCreate(sourceSetName)
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