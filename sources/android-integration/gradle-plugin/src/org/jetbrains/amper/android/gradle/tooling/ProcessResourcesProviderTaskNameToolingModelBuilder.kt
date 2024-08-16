/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android.gradle.tooling

import com.android.build.gradle.AppExtension
import com.android.build.gradle.BaseExtension
import org.gradle.api.Project
import org.gradle.tooling.provider.model.ToolingModelBuilder
import org.jetbrains.amper.android.ProcessResourcesProviderData
import org.jetbrains.amper.android.ProjectPath
import org.jetbrains.amper.android.TaskName
import org.jetbrains.amper.android.VariantName
import org.jetbrains.amper.android.gradle.projectPathToModule
import org.jetbrains.amper.android.gradle.request

data class ProcessResourcesProviderDataImpl(override val data: Map<ProjectPath, Map<VariantName, TaskName>>) :
    ProcessResourcesProviderData

class ProcessResourcesProviderTaskNameToolingModelBuilder : ToolingModelBuilder {

    override fun canBuild(modelName: String): Boolean = modelName == ProcessResourcesProviderData::class.java.name

    override fun buildAll(modelName: String, project: Project): ProcessResourcesProviderData {
        val projectPathToModule = project.gradle.projectPathToModule
        val request = project.gradle.request
        val stack = ArrayDeque<Project>()
        stack.add(project)
        val alreadyTraversed = mutableSetOf<Project>()

        return ProcessResourcesProviderDataImpl(
            buildMap {
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
                        .findByType(BaseExtension::class.java)
                        ?: error("Android extension not found in project $project")

                    val variants = when (androidExtension) {
                        is AppExtension -> androidExtension.applicationVariants
                        else -> error("Unsupported Android extension type")
                    }

                    val buildTypesChosen = request?.buildTypes?.map { it.value }?.toSet() ?: emptySet()
                    val chosenVariants =
                        variants.filter { variant -> buildTypesChosen.any { variant.name.startsWith(it) } }

                    put(p.path, buildMap {
                        chosenVariants.forEach {
                            val output = it.outputs.first()
                            put(it.name, output.processResourcesProvider.name)
                        }
                    })
                }
            }.filter { it.value.isNotEmpty() })
    }
}
