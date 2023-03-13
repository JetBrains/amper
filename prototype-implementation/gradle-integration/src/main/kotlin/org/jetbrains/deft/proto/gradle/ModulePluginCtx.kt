package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.Model
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet

/**
 * Shared module plugin properties.
 */
interface ModulePlugin {
    val project: Project
    val model: Model
    val moduleId: String
    val moduleIdToPath: Map<String, String>
    val targets: List<String>
    val allCollapsed: Map<String, List<String>>

    fun KotlinProjectExtension.applyForSourceSet(sourceSetName: String, action: KotlinSourceSet.() -> Unit) =
        getOrCreateSourceSet(sourceSetName).action()

    fun KotlinProjectExtension.getOrCreateSourceSet(sourceSetName: String): KotlinSourceSet =
        sourceSets.maybeCreate(sourceSetName)
}


open class ModulePluginCtx(
    override val project: Project,
    override val model: Model,
    override val moduleId: String,
    override val moduleIdToPath: Map<String, String>,
) : ModulePlugin {
    override val targets: List<String> by lazy { model.getTargets(moduleId) }
    override val allCollapsed: Map<String, List<String>> by lazy { model.getAllCollapsed(moduleId) }
}