/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ConvertCallChainIntoSequence")

package org.jetbrains.amper.tasks

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.DependencyNodeHolder
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.diagnostics.DoNotLogToTerminalCookie
import org.jetbrains.amper.diagnostics.setAmperModule
import org.jetbrains.amper.diagnostics.setFragments
import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.dr.resolver.DirectFragmentDependencyNodeHolder
import org.jetbrains.amper.frontend.dr.resolver.ModuleDependencyNodeWithModule
import org.jetbrains.amper.frontend.dr.resolver.emptyContext
import org.jetbrains.amper.frontend.dr.resolver.flow.toResolutionPlatform
import org.jetbrains.amper.frontend.mavenRepositories
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.maven.PublicationCoordinatesOverride
import org.jetbrains.amper.maven.PublicationCoordinatesOverrides
import org.jetbrains.amper.resolver.MavenResolver
import org.jetbrains.amper.resolver.getExternalDependencies
import org.jetbrains.amper.tasks.CommonTaskUtils.userReadableList
import org.jetbrains.amper.telemetry.setListAttribute
import org.jetbrains.amper.telemetry.spanBuilder
import org.jetbrains.amper.telemetry.use
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.io.path.Path
import kotlin.io.path.pathString
import kotlin.io.path.relativeToOrSelf

class ResolveExternalDependenciesTask(
    private val module: AmperModule,
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

        val compileDependencyCoordinates = fragmentsCompileModuleDependencies.getExternalDependencies()
        val runtimeDependencyCoordinates = fragmentsRuntimeModuleDependencies?.getExternalDependencies()
        return spanBuilder("resolve-dependencies")
            .setAmperModule(module)
            .setFragments(fragments)
            .setListAttribute("dependencies", compileDependencyCoordinates.map { it.toString() })
            .setListAttribute("runtimeDependencies", runtimeDependencyCoordinates?.map { it.toString() } ?: emptyList())
            .setAttribute("platform", resolvedPlatform.type.value)
            .also {
                resolvedPlatform.nativeTarget?.let { target ->
                    it.setAttribute("native-target", target)
                }
            }
            .setAttribute("native-target", resolvedPlatform.type.value)
            .setAttribute("user-cache-root", userCacheRoot.path.pathString)
            .use {
                logger.debug(
                    "resolve dependencies ${module.userReadableName} -- " +
                            "${fragments.userReadableList()} -- " +
                            "${compileDependencyCoordinates.joinToString(" ")} -- " +
                            "resolvePlatform=${resolvedPlatform.type.value} nativeTarget=${resolvedPlatform.nativeTarget}"
                )

                val configuration = mapOf(
                    "userCacheRoot" to userCacheRoot.path.pathString,
                    "compileDependencies" to compileDependencyCoordinates.joinToString("|"),
                    "runtimeDependencies" to (runtimeDependencyCoordinates?.joinToString("|") ?: ""),
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
                            emptyContext(userCacheRoot)
                        )

                        mavenResolver.resolve(
                            root = root,
                            resolveSourceMoniker = resolveSourceMoniker,
                        )

                        val compileDependenciesRootNode = root.children[0]
                        val runtimeDependenciesRootNode = if (root.children.size == 2) root.children[1] else null

                        val compileClasspath = compileDependenciesRootNode.dependencyPaths()
                        val runtimeClasspath = runtimeDependenciesRootNode?.dependencyPaths() ?: emptyList()

                        val publicationCoordsOverrides =
                            getPublicationCoordinatesOverrides(compileDependenciesRootNode, runtimeDependenciesRootNode)

                        return@execute ExecuteOnChangedInputs.ExecutionResult(
                            (compileClasspath + runtimeClasspath).toSet().sorted(),
                            outputProperties = mapOf(
                                "compile" to compileClasspath.joinToString(File.pathSeparator),
                                "runtime" to runtimeClasspath.joinToString(File.pathSeparator),
                                "publicationCoordsOverrides" to Json.encodeToString(publicationCoordsOverrides),
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
                                    compileDependencyCoordinates.joinToString("\n").prependIndent("  ")
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
                val publicationCoordsOverrides =
                    Json.decodeFromString<PublicationCoordinatesOverrides>(result.outputProperties["publicationCoordsOverrides"]!!)

                logger.debug("resolve dependencies ${module.userReadableName} -- " +
                        "${fragments.userReadableList()} -- " +
                        "${compileDependencyCoordinates.joinToString(" ")} -- " +
                        "resolvePlatform=$resolvedPlatform nativeTarget=${resolvedPlatform.nativeTarget}\n" +
                        "${repositories.joinToString(" ")} resolved to:\n${
                            compileClasspath.joinToString("\n") {
                                "  " + it.relativeToOrSelf(
                                    userCacheRoot.path
                                ).pathString
                            }
                        }"
                )

                // todo (AB) : output should contain placeholder for every module (in a correct place in the list!!!
                // todo (AB) : It might be replaced with the path to compiled module later in order to form complete correctly ordered classpath)
                Result(
                    compileClasspath = compileClasspath,
                    runtimeClasspath = runtimeClasspath,
                    coordinateOverridesForPublishing = publicationCoordsOverrides,
                )
            }

    }

    private fun getPublicationCoordinatesOverrides(
        compileDependenciesRootNode: DependencyNode,
        runtimeDependenciesRootNode: DependencyNode?,
    ): PublicationCoordinatesOverrides {
        val compileOverrides = compileDependenciesRootNode.children.getOverridesForDirectDeps()
        val runtimeOverrides = runtimeDependenciesRootNode
            ?.children
            ?.getOverridesForDirectDeps(directDependencyCondition = { notation?.compile == false })
            ?: emptyList()
        return PublicationCoordinatesOverrides(compileOverrides + runtimeOverrides)
    }

    private fun List<DependencyNode>.getOverridesForDirectDeps(
        directDependencyCondition: DirectFragmentDependencyNodeHolder.() -> Boolean = { true }
    ): List<PublicationCoordinatesOverride> = this
        .filterIsInstance<DirectFragmentDependencyNodeHolder>()
        .filter { it.dependencyNode is MavenDependencyNode }
        .filter { it.directDependencyCondition() }
        .mapNotNull { directMavenDependency ->
            val node = directMavenDependency.dependencyNode as MavenDependencyNode
            val coordinatesOriginal = node.getOriginalMavenCoordinates()
            val coordinatesForPublishing = node.getMavenCoordinatesForPublishing()
            if (coordinatesOriginal != coordinatesForPublishing) {
                PublicationCoordinatesOverride(
                    originalCoordinates = coordinatesOriginal,
                    variantCoordinates = coordinatesForPublishing,
                )
            } else {
                null
            }
        }

    class Result(
        val compileClasspath: List<Path>,
        val runtimeClasspath: List<Path>,
        val coordinateOverridesForPublishing: PublicationCoordinatesOverrides = PublicationCoordinatesOverrides(),
    ) : TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}