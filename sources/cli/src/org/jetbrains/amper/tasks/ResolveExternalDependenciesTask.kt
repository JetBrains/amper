/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ConvertCallChainIntoSequence")

package org.jetbrains.amper.tasks

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.useWithScope
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.diagnostics.DoNotLogToTerminalCookie
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setListAttribute
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.PotatoModule
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.resolver.getExternalDependencies
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrSelf

class ResolveExternalDependenciesTask(
    private val module: PotatoModule,
    private val userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs,
    private val platform: Platform,
    private val fragments: List<Fragment>,
    private val fragmentsCompileModuleDependencies: ModuleDependencyNodeWithModule,
    private val fragmentsRuntimeModuleDependencies: ModuleDependencyNodeWithModule?,
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

        // order in compileDependencies is important (classpath is generally (and unfortunately!) order-dependent),
        // but the current implementation requires a full review of it

        val resolvedPlatform = platform.toResolutionPlatform()
        if (resolvedPlatform == null) {
            logger.error("${module.userReadableName}: Non-leaf platform $platform is not supported for resolving external dependencies")
            return Result(compileClasspath = emptyList(), runtimeClasspath = emptyList())
        } else if (resolvedPlatform != ResolutionPlatform.JVM
            && resolvedPlatform != ResolutionPlatform.ANDROID
            && resolvedPlatform.nativeTarget == null
        ) {
            logger.error("${module.userReadableName}: $platform is not yet supported for resolving external dependencies")
            return Result(compileClasspath = emptyList(), runtimeClasspath = emptyList())
        }

        return spanBuilder("resolve-dependencies")
            .setAmperModule(module)
            .setListAttribute("dependencies", fragmentsCompileModuleDependencies.getExternalDependencies().map { it.toString() })
            .setListAttribute("runtimeDependencies", fragmentsRuntimeModuleDependencies?.getExternalDependencies()?.map { it.toString() } ?: emptyList<String>())
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
                            "${fragmentsCompileModuleDependencies.getExternalDependencies().map { it.toString() }.joinToString(" ")} -- " +
                            "resolvePlatform=${resolvedPlatform.type.value} nativeTarget=${resolvedPlatform.nativeTarget}"
                )

                val configuration = mapOf(
                    "userCacheRoot" to userCacheRoot.path.pathString,
                    "compileDependencies" to fragmentsCompileModuleDependencies.getExternalDependencies().joinToString("|"),
                    "runtimeDependencies" to (fragmentsRuntimeModuleDependencies?.getExternalDependencies()?.joinToString("|") ?: ""),
                    "repositories" to repositories.joinToString("|"),
                    "resolvePlatform" to resolvedPlatform.type.value,
                    "resolveNativeTarget" to (resolvedPlatform.nativeTarget ?: ""),
                )

                val result = try {
                    executeOnChangedInputs.execute(taskName.name, configuration, emptyList()) {
                        val resolveSourceMoniker = "module ${module.userReadableName}"
                        val root = DependencyNodeHolder(
                            name = "root",
                            children = listOfNotNull(
                                fragmentsCompileModuleDependencies,
                                fragmentsRuntimeModuleDependencies
                            ),
                        )

                        mavenResolver.resolve(
                            root = root,
                            resolveSourceMoniker = resolveSourceMoniker,
                        )

                        val compileClasspath = root.children[0].dependencyPaths()
                        val runtimeClasspath = if (root.children.size == 2) root.children[1].dependencyPaths() else emptyList()

                        return@execute ExecuteOnChangedInputs.ExecutionResult(
                            (compileClasspath + runtimeClasspath).toSet().sorted(),
                            outputProperties = mapOf(
                                "compile" to compileClasspath.joinToString(File.pathSeparator),
                                "runtime" to runtimeClasspath.joinToString(File.pathSeparator),
                            ),
                        )
                    }
                } catch (t: CancellationException) {
                    throw t
                } catch (t: Throwable) {
                    DoNotLogToTerminalCookie.use {
                        logger.error("resolve dependencies of module '${module.userReadableName}' failed\n" +
                                "fragments: ${fragments.userReadableList()}\n" +
                                "repositories:\n${repositories.joinToString("\n").prependIndent("  ")}\n" +
                                "direct dependencies:\n${
                                    fragmentsCompileModuleDependencies.getExternalDependencies(true).joinToString("\n").prependIndent("  ")
                                }\n" +
                                "all dependencies:\n${
                                    fragmentsCompileModuleDependencies.getExternalDependencies().joinToString("\n").prependIndent("  ")
                                }\n" +
                                "platform: $resolvedPlatform" +
                                (resolvedPlatform.nativeTarget?.let { "\nnativeTarget: $it" } ?: ""), t)
                    }

                    throw t
                }

                val compileClasspath =
                    result.outputProperties["compile"]!!.split(File.pathSeparator).filter { it.isNotEmpty() }
                        .map { Path(it) }
                val runtimeClasspath =
                    result.outputProperties["runtime"]!!.split(File.pathSeparator).filter { it.isNotEmpty() }
                        .map { Path(it) }

                logger.debug("resolve dependencies ${module.userReadableName} -- " +
                        "${fragments.userReadableList()} -- " +
                        "${fragmentsCompileModuleDependencies.getExternalDependencies().joinToString(" ")} -- " +
                        "resolvePlatform=$resolvedPlatform nativeTarget=${resolvedPlatform.nativeTarget}\n" +
                        "${repositories.joinToString(" ")} resolved to:\n${
                            compileClasspath.joinToString("\n") {
                                "  " + it.relativeToOrSelf(
                                    userCacheRoot.path
                                ).pathString
                            }
                        }"
                )

                // todo (AB) : output should contain placehoder for every module (in a correct place in the list!!!
                // todo (AB) : It might be replaced with the path to compiled module later in order to form complete correctly ordered classpath)
                Result(
                    compileClasspath = compileClasspath,
                    runtimeClasspath = runtimeClasspath,
                )
            }

    }

    class Result(
        val compileClasspath: List<Path>,
        val runtimeClasspath: List<Path>,
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}