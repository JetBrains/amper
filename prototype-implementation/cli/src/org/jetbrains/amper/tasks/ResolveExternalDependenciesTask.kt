/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.cli.TaskName
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

val defaultRepositories = listOf(
    "https://repo1.maven.org/maven2",
    "https://maven.google.com/",
    "https://maven.pkg.jetbrains.space/public/p/compose/dev"
)

class ResolveExternalDependenciesTask(
    private val module: PotatoModule,
    private val userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val platform: Platform,
    private val isTest: Boolean,
    override val taskName: TaskName,
): Task {

    private val mavenResolver by lazy {
        MavenResolver(userCacheRoot)
    }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val repositories = (module.parts.find<RepositoriesModulePart>()?.mavenRepositories?.map { it.url }
            ?: listOf()).ifEmpty { defaultRepositories }
        val fragments = module.fragments
            .filter { it.platforms.contains(platform) && it.isTest == isTest }
        val compileDependencies = fragments
            .flatMap { it.externalDependencies }
            .filterIsInstance<MavenDependency>()
            .filter { it.compile }
            .map { it.coordinates } +
                // TODO implicit dependencies on kotlin-stdlib ofc should be handled better
                listOf(
                    "org.jetbrains.kotlin:kotlin-stdlib:1.9.20",
                    "org.jetbrains.kotlin:kotlin-stdlib-common:1.9.20",
                )

        logger.info("resolve dependencies ${module.userReadableName} -- ${fragments.userReadableList()} -- ${compileDependencies.joinToString(" ")}")

        // order in compileDependencies is important (classpath is generally (and unfortunately!) order-dependent)

        val configuration = mapOf(
            "dependencies" to compileDependencies.joinToString("|"),
        )

        val paths = executeOnChangedInputs.execute(taskName.toString(), configuration, emptyList()) {
            return@execute ExecuteOnChangedInputs.ExecutionResult(mavenResolver.resolve(compileDependencies, repositories).toList())
        }.outputs

        logger.info("resolve dependencies ${module.userReadableName} -- ${fragments.userReadableList()} -- ${compileDependencies.joinToString(" ")} -- ${repositories.joinToString(" ")} resolved to:\n${paths.joinToString("\n") { "  " + it.relativeTo(userCacheRoot.path).pathString }}")

        return TaskResult(classpath = paths, dependencies = dependenciesResult)
    }

    class TaskResult(override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
                     val classpath: List<Path>,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
