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