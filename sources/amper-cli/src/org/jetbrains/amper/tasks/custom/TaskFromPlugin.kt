/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.toStableJsonLikeString
import org.jetbrains.amper.frontend.plugins.GeneratedPathKind
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.api.Artifact
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.tasks.artifacts.api.ArtifactTask
import org.jetbrains.amper.tasks.artifacts.api.ArtifactType
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.slf4j.LoggerFactory
import java.lang.reflect.InvocationTargetException
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.reflect.jvm.javaType
import kotlin.reflect.jvm.kotlinFunction

class TaskFromPlugin(
    override val taskName: TaskName,
    val module: AmperModule,
    val description: TaskFromPluginDescription,
    val buildOutputRoot: AmperBuildOutputRoot,
    val incrementalCache: IncrementalCache,
    val terminal: Terminal,
) : ArtifactTask {

    interface ExternalTaskArtifact : Artifact

    class ExternalTaskRawArtifact(
        override val path: Path,
    ) : ExternalTaskArtifact

    class ExternalTaskGeneratedKotlinJavaSourcesArtifact(
        buildOutputRoot: AmperBuildOutputRoot,
        fragment: Fragment,
        path: Path,
    ) : KotlinJavaSourceDirArtifact(buildOutputRoot, fragment, path), ExternalTaskArtifact

    class ExternalTaskGeneratedJvmResourcesArtifact(
        buildOutputRoot: AmperBuildOutputRoot,
        fragment: Fragment,
        path: Path,
    ) : JvmResourcesDirArtifact(buildOutputRoot, fragment, path), ExternalTaskArtifact

    override val consumes: List<ArtifactSelector<*, *>> = description.inputs
        .map { inputPath ->
            ArtifactSelector(
                type = ArtifactType(ExternalTaskArtifact::class),
                predicate = {
                    // Child paths of the produced path are also allowed
                    inputPath.startsWith(it.path)
                },
                description = "with path '${inputPath}'",
                quantifier = if (inputPath.startsWith(buildOutputRoot.path)) {
                    // If the inputPath points somewhere in the build directory - then this path has to be built by
                    //  something.
                    //  If it's not built, then it's probably an error in path wiring.
                    Quantifier.Single
                } else {
                    // If the inputPath points somewhere not in the build directory (e.g., in the source tree),
                    //  then we do not require it to be built by anything.
                    // This is needed to allow having some custom sources as inputs without introducing any public
                    //  mechanism to declare them as "provided".
                    Quantifier.AnyOrNone
                },
            )
        }

    override val produces: List<Artifact> = description.outputs
        .map { (output, mark) ->
            when (mark?.kind) {
                null -> ExternalTaskRawArtifact(output)
                GeneratedPathKind.KotlinSources,
                GeneratedPathKind.JavaSources -> ExternalTaskGeneratedKotlinJavaSourcesArtifact(
                    buildOutputRoot = buildOutputRoot,
                    fragment = mark.associateWith,
                    path = output,
                )
                GeneratedPathKind.JvmResources -> ExternalTaskGeneratedJvmResourcesArtifact(
                    buildOutputRoot = buildOutputRoot,
                    fragment = mark.associateWith,
                    path = output,
                )
            }
        }

    override fun injectConsumes(artifacts: Map<ArtifactSelector<*, *>, List<Artifact>>) {
        // No need to consume anything, because we match inputs by paths already
    }

    override suspend fun run(
        dependenciesResult: List<TaskResult>,
        executionContext: TaskGraphExecutionContext,
    ): TaskResult {
        val runtimeClasspathsByModule = dependenciesResult
            .filterIsInstance<JvmRuntimeClasspathTask.Result>()
            .associateBy { it.module }
        val customResolutionByRequest = dependenciesResult
            .filterIsInstance<ResolveCustomExternalDependenciesTask.Result>()
            .associateBy { it.destination }

        val taskCode = runtimeClasspathsByModule[description.codeSource]!!

        val doNotUseExecutionAvoidance = description.explicitOptOutOfExecutionAvoidance ||
                // We do not use execution-avoidance if there are no outputs declared.
                description.outputs.isEmpty()

        if (doNotUseExecutionAvoidance) {
            logger.debug("No outputs declared, not using execution avoidance")
            doExecuteTaskAction(
                taskRuntimeClasspath = taskCode.jvmRuntimeClasspath,
            )
            return EmptyTaskResult
        }

        for (classpathRequest in description.requestedClasspaths) {
            classpathRequest.node.resolvedFiles = buildList {
                addAll(customResolutionByRequest[classpathRequest]?.resolvedFiles.orEmpty())
                classpathRequest.localDependencies.forEach { depModule ->
                    addAll(runtimeClasspathsByModule[depModule]!!.jvmRuntimeClasspath)
                }
            }
        }

        incrementalCache.execute(
            key = taskName.name,
            inputValues = mapOf(
                "action" to description.actionClassJvmName + '.' + description.actionFunctionJvmName,
                "arguments" to description.actionArguments.entries.joinToString(
                    separator = "\n",
                    transform = { (arg, value) ->
                        val valueRepresentation = when(value) {
                            is SchemaNode -> value.toStableJsonLikeString()
                            else -> value.toString()
                        }
                        "$arg = $valueRepresentation;"
                    },
                ),
            ),
            inputFiles = buildList {
                addAll(description.inputs)
                for (result in runtimeClasspathsByModule.values) {
                    addAll(result.jvmRuntimeClasspath)
                }
            },
        ) {
            doExecuteTaskAction(
                taskRuntimeClasspath = taskCode.jvmRuntimeClasspath,
            )
            IncrementalCache.ExecutionResult(
                outputFiles = description.outputs.keys.toList(),
            )
        }

        return EmptyTaskResult
    }

    private fun doExecuteTaskAction(
        taskRuntimeClasspath: List<Path>,
    ) {
        // TODO: Cache the classloader per plugin?
        val classLoader = URLClassLoader(
            taskName.toString(),
            taskRuntimeClasspath.map { it.toUri().toURL() }.toTypedArray(),
            null,
        )

        val actionFacade = classLoader.loadClass(description.actionClassJvmName)
        val actionMethod = actionFacade.methods.first { it.name == description.actionFunctionJvmName }.kotlinFunction!!
        val marshaller = ValueMarshaller(classLoader)
        val argumentsMap = description.actionArguments.mapKeys { (name, _) ->
            actionMethod.parameters.first { it.name == name }
        }.mapValues { (parameter, value) ->
            marshaller.marshallValue(value, parameter.type.javaType)
        }

        try {
            actionMethod.callBy(argumentsMap)
        } catch (e: InvocationTargetException) {
            userReadableError(e.targetException.stackTraceToString())
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}