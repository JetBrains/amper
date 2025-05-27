/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.java

import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.schema.ModuleJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.enabled
import org.jetbrains.amper.tasks.CommonTaskType
import org.jetbrains.amper.tasks.ProjectTasksBuilder
import org.jetbrains.amper.tasks.jvm.JvmSpecificTaskType

fun ProjectTasksBuilder.setupJavaAnnotationProcessingTasks() {
    allModules()
        .alsoPlatforms()
        .alsoTests()
        .withEach {
            val fragments = module.fragments.filter { it.isTest == isTest && it.platforms.contains(platform) }
            if (fragments.none { it.settings.java.annotationProcessing.enabled }) {
                return@withEach
            }
            val processorDRTaskName = JvmSpecificTaskType.JavaAnnotationProcessorDependencies.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = ResolveJavaAnnotationProcessorDependenciesTask(
                    taskName = processorDRTaskName,
                    module = module,
                    fragments = fragments,
                    platform = platform,
                    userCacheRoot = context.userCacheRoot,
                    executeOnChangedInputs = executeOnChangedInputs,
                )
            )

            val processorModuleDepsPaths = fragments.flatMap { it.settings.java.annotationProcessing.processors }
                .filterIsInstance<ModuleJavaAnnotationProcessorDeclaration>()
                .map { it.path }
            val processorModuleDeps = model.modules.filter { it.source.moduleDir in processorModuleDepsPaths }

            val processorClasspathTaskName = JvmSpecificTaskType.JavaAnnotationProcessorClasspath.getTaskName(module, platform, isTest)
            tasks.registerTask(
                task = JavaAnnotationProcessorClasspathTask(processorClasspathTaskName),
                dependsOn = buildList {
                    add(processorDRTaskName)
                    addAll(processorModuleDeps.map { processorModuleDep ->
                        CommonTaskType.RuntimeClasspath.getTaskName(
                            module = processorModuleDep,
                            platform = Platform.JVM,
                            isTest = false,
                        )
                    })
                }
            )
        }
}