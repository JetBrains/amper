/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ConvertCallChainIntoSequence")

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.io.File
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
    private val fragments: List<Fragment>,
    private val fragmentsCompileModuleDependencies: List<PotatoModule>,
    override val taskName: TaskName,
): Task {

    private val mavenResolver by lazy {
        MavenResolver(userCacheRoot)
    }

    override suspend fun run(dependenciesResult: List<org.jetbrains.amper.tasks.TaskResult>): org.jetbrains.amper.tasks.TaskResult {
        val repositories = (module.parts.find<RepositoriesModulePart>()?.mavenRepositories?.map { it.url }?.distinct()?.sorted()
            ?: listOf()).ifEmpty { defaultRepositories }

        val directCompileDependencies = fragments
            .flatMap { it.externalDependencies }
            .filterIsInstance<MavenDependency>()
            .filter { it.compile }
            .map { it.coordinates }
            .distinct()

        val exportedDependencies = fragmentsCompileModuleDependencies
            .flatMap { module -> module.fragments.filter { it.platforms.contains(platform) && !it.isTest } }
            .flatMap { it.externalDependencies }
            .filterIsInstance<MavenDependency>()
            .filter { it.compile && it.exported }
            .map { it.coordinates }
            .distinct()

        val dependenciesToResolve = exportedDependencies + directCompileDependencies

        // order in compileDependencies is important (classpath is generally (and unfortunately!) order-dependent),
        // but the current implementation requires a full review of it

        val (resolvePlatform,  resolveNativeTarget) = when {
            platform == Platform.JVM || platform == Platform.ANDROID -> "jvm" to null
            platform.topmostParentNoCommon == Platform.NATIVE -> "native" to platform.name.lowercase()
            else -> {
                logger.error("${module.userReadableName}: $platform is not yet supported for resolving external dependencies")
                return TaskResult(compileClasspath = emptyList(), runtimeClasspath = emptyList(), dependencies = dependenciesResult)
            }
        }

        logger.info("resolve dependencies ${module.userReadableName} -- " +
                "${fragments.userReadableList()} -- " +
                "${directCompileDependencies.sorted().joinToString(" ")} -- " +
                exportedDependencies.sorted().joinToString(" ") + " -- " +
                "resolvePlatform=$resolvePlatform nativeTarget=$resolveNativeTarget")

        val configuration = mapOf(
            "dependencies" to dependenciesToResolve.joinToString("|"),
            "repositories" to repositories.joinToString("|"),
            "resolvePlatform" to resolvePlatform,
            "resolveNativeTarget" to (resolveNativeTarget ?: ""),
        )

        val result = try {
            executeOnChangedInputs.execute(taskName.name, configuration, emptyList()) {
                val compileClasspath = mavenResolver.resolve(
                    coordinates = dependenciesToResolve,
                    repositories = repositories,
                    scope = ResolutionScope.COMPILE,
                    platform = resolvePlatform,
                    nativeTarget = resolveNativeTarget,
                ).toList()
                val runtimeClasspath = mavenResolver.resolve(
                    coordinates = dependenciesToResolve,
                    repositories = repositories,
                    scope = ResolutionScope.RUNTIME,
                    platform = resolvePlatform,
                    nativeTarget = resolveNativeTarget,
                ).toList()
                return@execute ExecuteOnChangedInputs.ExecutionResult(
                    (compileClasspath + runtimeClasspath).toSet().sorted(),
                    outputProperties = mapOf(
                        "compile" to compileClasspath.joinToString(File.pathSeparator),
                        "runtime" to runtimeClasspath.joinToString(File.pathSeparator),
                    ),
                )
            }
        } catch (t: Throwable) {
            throw IllegalStateException("resolve dependencies of module '${module.userReadableName}' failed\n" +
                    "fragments: ${fragments.userReadableList()}\n" +
                    "repositories:\n${repositories.joinToString("\n").prependIndent("  ")}\n" +
                    "direct dependencies:\n${directCompileDependencies.sorted().joinToString("\n").prependIndent("  ")}\n" +
                    "exported dependencies:\n${exportedDependencies.sorted().joinToString("\n").prependIndent("  ")}\n" +
                    "platform: $resolvePlatform\n" +
                    "nativeTarget: $resolveNativeTarget", t)
        }

        val compileClasspath = result.outputProperties["compile"]!!.split(File.pathSeparator).filter { it.isNotEmpty() }.map { Path.of(it) }
        val runtimeClasspath = result.outputProperties["runtime"]!!.split(File.pathSeparator).filter { it.isNotEmpty() }.map { Path.of(it) }

        logger.debug("resolve dependencies ${module.userReadableName} -- " +
                "${fragments.userReadableList()} -- " +
                "${dependenciesToResolve.joinToString(" ")} -- " +
                "resolvePlatform=$resolvePlatform nativeTarget=$resolveNativeTarget\n" +
                "${repositories.joinToString(" ")} resolved to:\n${compileClasspath.joinToString("\n") { "  " + it.relativeTo(userCacheRoot.path).pathString }}")

        return TaskResult(compileClasspath = compileClasspath, runtimeClasspath = runtimeClasspath, dependencies = dependenciesResult)
    }

    class TaskResult(override val dependencies: List<org.jetbrains.amper.tasks.TaskResult>,
                     val compileClasspath: List<Path>,
                     val runtimeClasspath: List<Path>,
    ) : org.jetbrains.amper.tasks.TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
