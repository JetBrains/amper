package org.example

import org.example.api.Model
import org.gradle.api.Project
import org.jetbrains.kotlin.gradle.dsl.KotlinProjectExtension

/**
 * Plugin logic, bind to specific module, when only default target is available.
 */
class OnlyKotlinModulePlugin(
    ctx: ModulePluginCtx,
) : ModulePlugin by ctx {

    private val kotlinPE: KotlinProjectExtension = project.extensions.getByType(KotlinProjectExtension::class.java)

    fun apply() {
        // Add dependencies for main source set.
        kotlinPE.getOrCreateSourceSet("main").dependencies {
            model.getDeclaredDependencies(moduleId, Model.defaultTarget).forEach { dependency ->
                addDependency(moduleIdToPath, dependency)
            }
        }
    }
}