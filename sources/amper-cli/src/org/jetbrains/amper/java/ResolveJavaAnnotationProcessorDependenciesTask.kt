/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.java

import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.telemetry.setFragments
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.toRepositories
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.frontend.schema.CatalogJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.frontend.schema.MavenJavaAnnotationProcessorDeclaration
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.incrementalcache.executeForFiles
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.pathString

// TODO: the same problem like with ResolveKspProcessorDependenciesTask
internal class ResolveJavaAnnotationProcessorDependenciesTask(
    override val taskName: TaskName,
    private val module: AmperModule,
    private val fragments: List<Fragment>,
    private val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
): Task {
    private val mavenResolver = MavenResolver(userCacheRoot)

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val repositories = module.mavenRepositories.filter { it.resolve }.map { it.url }.distinct()

        val processorCoords = fragments.flatMap { it.settings.java.annotationProcessing.processors }
            .flatMap {
                when (it) {
                    is MavenJavaAnnotationProcessorDeclaration -> listOf(it.coordinates.value)
                    is CatalogJavaAnnotationProcessorDeclaration -> listOf(it.catalogKey.value)
                    else -> emptyList()
                }
            }

        if (processorCoords.isEmpty()) {
            return Result(javaAnnotationProcessorExternalJars = emptyList())
        }

        val configuration = mapOf(
            "userCacheRoot" to userCacheRoot.path.pathString,
            "repositories" to repositories.joinToString("|"),
            "fragments" to fragments.joinToString("|") { it.name },
            "processors" to processorCoords.joinToString("|"),
        )
        val javaAnnotationProcessorExternalJars = executeOnChangedInputs.executeForFiles(
            "${taskName.name}-resolve-java-annotation-processors",
            configuration,
            emptyList()
        ) {
            spanBuilder("resolve-java-annotation-processors")
                .setAmperModule(module)
                .setFragments(fragments)
                .setAttribute("platform", platform.pretty)
                .setListAttribute("java-annotation-processor-dependencies", processorCoords)
                .use {
                    mavenResolver.resolve(
                        coordinates = processorCoords,
                        repositories = repositories.toRepositories(),
                        platform = ResolutionPlatform.JVM,
                        scope = ResolutionScope.RUNTIME,
                        resolveSourceMoniker = "Java annotation processors for ${module.userReadableName}-${platform.pretty}",
                    )
                }
        }

        return Result(javaAnnotationProcessorExternalJars = javaAnnotationProcessorExternalJars)
    }

    class Result(val javaAnnotationProcessorExternalJars: List<Path>) : TaskResult
}
