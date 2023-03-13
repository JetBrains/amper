package org.jetbrains.deft.proto.gradle

import org.jetbrains.deft.proto.frontend.Model
import org.jetbrains.kotlin.gradle.plugin.KotlinDependencyHandler

internal fun KotlinDependencyHandler.addDependency(moduleIdToPath: Map<String, String>, dependency: String) {
    if (dependency.startsWith("[local]")) {
        val dependencyModuleId = dependency.removePrefix("[local]")
        val dependencyProjectPath = moduleIdToPath[dependencyModuleId] ?: dependencyModuleId
        implementation(project(dependencyProjectPath))
    } else
        implementation(dependency)
}

/**
 * Check is module contains only default target.
 */
fun Model.isOnlyKotlinModule(moduleId: String) =
    getTargets(moduleId).size == 1 && getTargets(moduleId).contains(Model.defaultTarget)

/**
 * Check is module contains android target.
 */
fun Model.hasAndroid(moduleId: String) = getTargets(moduleId).contains("android")

/**
 * Check is module contains android target.
 */
fun Model.isApplication(moduleId: String) = getModuleInfo(moduleId)["type"]?.first() == "application"