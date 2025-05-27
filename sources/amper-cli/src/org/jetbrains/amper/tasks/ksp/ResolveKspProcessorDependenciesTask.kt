/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.ksp

import org.jetbrains.amper.cli.telemetry.setAmperModule
import org.jetbrains.amper.cli.telemetry.setFragments
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.dr.resolver.flow.toRepository
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.frontend.schema.MavenKspProcessorDeclaration
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.incrementalcache.executeForFiles
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.pathString

// TODO merge with regular resolveDependencies task?
//  Problem: this is just for JVM, but the regular DR is done for each platform
internal class ResolveKspProcessorDependenciesTask(
    override val taskName: TaskName,
    private val module: AmperModule,
    private val fragments: List<Fragment>,
    private val platform: Platform,
    private val userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
): Task {
    private val mavenResolver = MavenResolver(userCacheRoot)

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        val repositories = module.mavenRepositories.filter { it.resolve }.map { it.toRepository() }.distinct()

        // catalog references have been handled in the frontend, so we don't need to resolve them here
        val processorCoords = fragments.flatMap { it.settings.kotlin.ksp.processors }
            .filterIsInstance<MavenKspProcessorDeclaration>()
            .map { it.coordinates }

        if (processorCoords.isEmpty()) {
            return Result(kspProcessorExternalJars = emptyList())
        }

        val configuration = mapOf(
            "userCacheRoot" to userCacheRoot.path.pathString,
            "repositories" to repositories.joinToString("|"),
            "fragments" to fragments.joinToString("|") { it.name },
            "processors" to processorCoords.joinToString("|"),
        )
        val kspProcessorExternalJars = executeOnChangedInputs.executeForFiles(
            "${taskName.name}-resolve-ksp-processors",
            configuration,
            emptyList()
        ) {
            spanBuilder("resolve-ksp-processors")
                .setAmperModule(module)
                .setFragments(fragments)
                .setAttribute("platform", platform.pretty)
                .setListAttribute("ksp-processor-dependencies", processorCoords)
                .use {
                    mavenResolver.resolve(
                        coordinates = processorCoords,
                        repositories = repositories,
                        platform = ResolutionPlatform.JVM, // processors are JVM-based even when processing KMP code
                        scope = ResolutionScope.RUNTIME,
                        resolveSourceMoniker = "KSP processors for ${module.userReadableName}-${platform.pretty}",
                    )
                }
        }

        return Result(kspProcessorExternalJars = kspProcessorExternalJars)
    }

    class Result(val kspProcessorExternalJars: List<Path>) : TaskResult
}
