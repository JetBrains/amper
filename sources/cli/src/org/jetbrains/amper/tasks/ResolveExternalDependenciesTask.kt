/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ConvertCallChainIntoSequence")

package org.jetbrains.amper.tasks

import org.jetbrains.amper.cli.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.diagnostics.spanBuilder
import org.jetbrains.amper.diagnostics.useWithScope
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.engine.TaskName
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.resolver.toResolutionPlatform
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrSelf

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

    override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
        val repositories = module.mavenRepositories
            .filter { it.resolve }
            .map { it.url }
            .distinct()

        val directCompileDependencies = fragments
            .flatMap { it.externalDependencies }
            .filterIsInstance<MavenDependency>()
            // todo (AB) : Why compile deps only? what if there is a runtime direct dependency?
            // todo (AB) : It should be passed to DR.resolve for runtime scope
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

        val resolvedPlatform = platform.toResolutionPlatform()
        if (resolvedPlatform == null) {
            logger.error("${module.userReadableName}: Non-leaf platform $platform is not supported for resolving external dependencies")
            return Result(compileClasspath = emptyList(), runtimeClasspath = emptyList(), dependencies = dependenciesResult)
        } else if (resolvedPlatform != ResolutionPlatform.JVM
            && resolvedPlatform != ResolutionPlatform.ANDROID
            && resolvedPlatform.nativeTarget == null
        ) {
            logger.error("${module.userReadableName}: $platform is not yet supported for resolving external dependencies")
            return Result(compileClasspath = emptyList(), runtimeClasspath = emptyList(), dependencies = dependenciesResult)
        }

        return spanBuilder("resolve-dependencies")
            .setAmperModule(module)
            .setListAttribute("dependencies", dependenciesToResolve)
            .setListAttribute("fragments", fragments.map { it.name }.sorted())
            .setAttribute("platform", resolvedPlatform.type.value)
            .also {
                resolvedPlatform.nativeTarget?.let { target ->
                    it.setAttribute("native-target", target)
                }
            }
            .setAttribute("native-target", resolvedPlatform.type.value)
            .setAttribute("user-cache-root", userCacheRoot.path.pathString)
            .useWithScope {
                logger.debug(
                    "resolve dependencies ${module.userReadableName} -- " +
                            "${fragments.userReadableList()} -- " +
                            "${directCompileDependencies.sorted().joinToString(" ")} -- " +
                            exportedDependencies.sorted().joinToString(" ") + " -- " +
                            "resolvePlatform=${resolvedPlatform.type.value} nativeTarget=${resolvedPlatform.nativeTarget}"
                )

                val configuration = mapOf(
                    "userCacheRoot" to userCacheRoot.path.pathString,
                    "dependencies" to dependenciesToResolve.joinToString("|"),
                    "repositories" to repositories.joinToString("|"),
                    "resolvePlatform" to resolvedPlatform.type.value,
                    "resolveNativeTarget" to (resolvedPlatform.nativeTarget ?: ""),
                )

                val result = try {
                    val resolveSourceMoniker = "module ${module.userReadableName}"
                    executeOnChangedInputs.execute(taskName.name, configuration, emptyList()) {
                        val compileClasspath = mavenResolver.resolve(
                            coordinates = dependenciesToResolve,
                            repositories = repositories,
                            scope = ResolutionScope.COMPILE,
                            platform = resolvedPlatform,
                            resolveSourceMoniker = resolveSourceMoniker,
                        ).toList()
                        val runtimeClasspath = mavenResolver.resolve(
                            coordinates = dependenciesToResolve,
                            repositories = repositories,
                            scope = ResolutionScope.RUNTIME,
                            platform = resolvedPlatform,
                            resolveSourceMoniker = resolveSourceMoniker,
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
                            "direct dependencies:\n${
                                directCompileDependencies.sorted().joinToString("\n").prependIndent("  ")
                            }\n" +
                            "exported dependencies:\n${
                                exportedDependencies.sorted().joinToString("\n").prependIndent("  ")
                            }\n" +
                            "platform: $resolvedPlatform" +
                            (resolvedPlatform.nativeTarget?.let { "\nnativeTarget: $it" } ?: ""), t)
                }

                val compileClasspath =
                    result.outputProperties["compile"]!!.split(File.pathSeparator).filter { it.isNotEmpty() }
                        .map { Path.of(it) }
                val runtimeClasspath =
                    result.outputProperties["runtime"]!!.split(File.pathSeparator).filter { it.isNotEmpty() }
                        .map { Path.of(it) }

                logger.debug("resolve dependencies ${module.userReadableName} -- " +
                        "${fragments.userReadableList()} -- " +
                        "${dependenciesToResolve.joinToString(" ")} -- " +
                        "resolvePlatform=$resolvedPlatform nativeTarget=${resolvedPlatform.nativeTarget}\n" +
                        "${repositories.joinToString(" ")} resolved to:\n${
                            compileClasspath.joinToString("\n") {
                                "  " + it.relativeToOrSelf(
                                    userCacheRoot.path
                                ).pathString
                            }
                        }"
                )

                Result(
                    compileClasspath = compileClasspath,
                    runtimeClasspath = runtimeClasspath,
                    dependencies = dependenciesResult
                )
            }
    }

    class Result(override val dependencies: List<TaskResult>,
                 val compileClasspath: List<Path>,
                 val runtimeClasspath: List<Path>,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
