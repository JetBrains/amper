/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package tooling

import ApkPathAndroidBuildResult
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.jetbrains.amper.frontend.ProductType
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

                projectPathToModule[p.path]?.let {
                    if (p.path in (project.gradle.request?.modules?.map { it.modulePath }?.toSet() ?: setOf())) {
                        for(buildTypeValue in request?.buildTypes?.map { it.value } ?: setOf()) {
                            when (it.type) {
                                ProductType.ANDROID_APP -> add(
                                    p
                                        .layout
                                        .buildDirectory
                                        .get()
                                        .asFile
                                        .toPath()
                                        .resolve("outputs/apk/$buildTypeValue/${p.name}-$buildTypeValue.apk")
                                        .toAbsolutePath()
                                        .toString()
                                )

                                else -> add(
                                    p
                                        .layout
                                        .buildDirectory
                                        .get()
                                        .asFile
                                        .toPath()
                                        .resolve("outputs/aar/${p.name}-$buildTypeValue.aar")
                                        .toAbsolutePath()
                                        .toString()
                                )
                            }
                        }
                    }

                    for (subproject in p.subprojects) {
                        if (subproject !in alreadyTraversed) {
                            stack.add(subproject)
                            alreadyTraversed.add(subproject)
                        }
                    }
                }

            }
        }

        return AndroidBuildResultImpl(paths)
    }
}