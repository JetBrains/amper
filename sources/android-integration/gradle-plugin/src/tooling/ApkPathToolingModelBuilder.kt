/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package tooling

import ApkPathAndroidBuildResult
import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.LibraryExtension
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.jetbrains.amper.frontend.schema.ProductType
import projectPathToModule
import request


data class AndroidBuildResultImpl(override val paths: List<String>) : ApkPathAndroidBuildResult

class ApkPathToolingModelBuilder : ToolingModelBuilder {
    override fun canBuild(modelName: String): Boolean = ApkPathAndroidBuildResult::class.java.name == modelName

    override fun buildAll(modelName: String, project: Project): ApkPathAndroidBuildResult {
        val projectPathToModule = project.gradle.projectPathToModule
        val request = project.gradle.request
        val stack = ArrayDeque<Project>()
        stack.add(project)
        val alreadyTraversed = mutableSetOf<Project>()

        val paths = buildList {
            while (stack.isNotEmpty()) {
                val p = stack.removeFirst()
                if (p.path !in (request?.modules?.map { it.modulePath }?.toSet() ?: setOf())) {
                    continue
                }
                val module = projectPathToModule[p.path] ?: continue

                val androidExtension = project
                    .extensions
                    .findByType(BaseExtension::class.java) ?: error("Android extension not found in project $project")

                val variants = when (androidExtension) {
                    is AppExtension -> androidExtension.applicationVariants
                    is LibraryExtension -> androidExtension.libraryVariants
                    else -> error("Unsupported Android extension type")
                }

                val buildTypesChosen = request?.buildTypes?.map { it.value }?.toSet() ?: emptySet()
                val chosenVariants = variants.filter { variant -> buildTypesChosen.any { variant.name.startsWith(it) } }

                chosenVariants
                    .flatMap { it.outputs }
                    .map { it.outputFile }
                    .map { it.toString() }
                    .forEach { add(it) }

                for (subproject in p.subprojects) {
                    if (subproject !in alreadyTraversed) {
                        stack.add(subproject)
                        alreadyTraversed.add(subproject)
                    }
                }
            }
        }
        return AndroidBuildResultImpl(paths)
    }
}