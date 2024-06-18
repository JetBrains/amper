/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.gradle.tooling

import org.jetbrains.amper.android.AndroidBuildResult
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.api.BaseVariant
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.jetbrains.amper.android.gradle.projectPathToModule
import org.jetbrains.amper.android.gradle.request


abstract class BaseAndroidToolingModelBuilder: ToolingModelBuilder {
    override fun buildAll(modelName: String, project: Project): AndroidBuildResult {
        val projectPathToModule = project.gradle.projectPathToModule
        val request = project.gradle.request
        val stack = ArrayDeque<Project>()
        stack.add(project)
        val alreadyTraversed = mutableSetOf<Project>()

        val paths = buildList {
            while (stack.isNotEmpty()) {
                val p = stack.removeFirst()
                for (subproject in p.subprojects) {
                    if (subproject !in alreadyTraversed) {
                        stack.add(subproject)
                        alreadyTraversed.add(subproject)
                    }
                }
                if (p.path !in (request?.modules?.map { it.modulePath }?.toSet() ?: setOf())) {
                    continue
                }
                projectPathToModule[p.path] ?: continue

                val androidExtension = p
                    .extensions
                    .findByType(BaseExtension::class.java) ?: error("Android extension not found in project $project")

                val variants = when (androidExtension) {
                    is AppExtension -> androidExtension.applicationVariants
                    is LibraryExtension -> androidExtension.libraryVariants
                    else -> error("Unsupported Android extension type")
                }

                val buildTypesChosen = request?.buildTypes?.map { it.value }?.toSet() ?: emptySet()
                val chosenVariants = variants.filter { variant -> buildTypesChosen.any { variant.name.startsWith(it) } }

                chosenVariants.getArtifactsFromVariants().forEach { add(it) }
            }
        }
        return buildResult(paths)
    }

    protected abstract fun List<BaseVariant>.getArtifactsFromVariants(): List<String>
    protected abstract fun buildResult(paths: List<String>): AndroidBuildResult
}