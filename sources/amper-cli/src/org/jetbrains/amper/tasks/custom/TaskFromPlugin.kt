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
import org.jetbrains.amper.tasks.jvm.JvmClassesJarTask
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.util.StandardStreamsCapture
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


    class ExternalTaskGeneratedKotlinJavaSourcesArtifact(
        buildOutputRoot: AmperBuildOutputRoot,
        fragment: Fragment,
        path: Path,
    ) : KotlinJavaSourceDirArtifact(buildOutputRoot, fragment, path)

    class ExternalTaskGeneratedJvmResourcesArtifact(
        buildOutputRoot: AmperBuildOutputRoot,
        fragment: Fragment,
        path: Path,
    ) : JvmResourcesDirArtifact(buildOutputRoot, fragment, path)

    override val consumes: List<ArtifactSelector<*, *>> = emptyList()

    override val produces: List<Artifact> = description.outputs
        .mapNotNull { (output, mark) ->
            when (mark?.kind) {
                null -> null
                GeneratedPathKind.KotlinSources,
                GeneratedPathKind.JavaSources,
                    -> ExternalTaskGeneratedKotlinJavaSourcesArtifact(
                    buildOutputRoot = buildOutputRoot,
                    fragment = mark.associateWith,
                    path = output.value,
                )
                GeneratedPathKind.JvmResources -> ExternalTaskGeneratedJvmResourcesArtifact(
                    buildOutputRoot = buildOutputRoot,
                    fragment = mark.associateWith,
                    path = output.value,
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
        val taskCode = dependenciesResult
            .filterIsInstance<JvmRuntimeClasspathTask.Result>()
            .single()

        description.requestedCompilationArtifacts.forEach { request ->
            val result = dependenciesResult.filterIsInstance<JvmClassesJarTask.Result>()
                .first { it.module == request.from }
            request.node.artifact = result.jarPath
        }

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

        incrementalCache.execute(
            key = taskName.name,
            inputValues = mapOf(
                "action" to description.actionClassJvmName + '.' + description.actionFunctionJvmName,
                "arguments" to description.actionArguments.entries.joinToString(
                    separator = "\n",
                    transform = { (arg, value) ->
                        val valueRepresentation = when (value) {
                            is SchemaNode -> value.toStableJsonLikeString()
                            else -> value.toString()
                        }
                        "$arg = $valueRepresentation;"
                    },
                ),
            ),
            inputFiles = buildList {
                addAll(taskCode.jvmRuntimeClasspath)
                for (input in description.inputs) add(input.path.value)
                for (resolvedRequest in description.requestedClasspaths) addAll(resolvedRequest.node.resolvedFiles)
                for (sourcesRequest in description.requestedModuleSources) addAll(sourcesRequest.node.sourceDirectories)
                for (artifactRequest in description.requestedCompilationArtifacts) add(artifactRequest.node.artifact)
            },
        ) {
            doExecuteTaskAction(
                taskRuntimeClasspath = taskCode.jvmRuntimeClasspath,
            )
            IncrementalCache.ExecutionResult(
                outputFiles = description.outputs.map { it.path.value },
            )
        }

        return EmptyTaskResult
    }

    private fun doExecuteTaskAction(
        taskRuntimeClasspath: List<Path>,
    ) {
        // TODO: Cache the classloader per plugin?
        val classLoader = URLClassLoader(
            taskName.name,
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
            StandardStreamsCapture.capturing(
                onStderrLine = { logger.error(it) },
                onStdoutLine = { logger.info(it) },
            ) {
                actionMethod.callBy(argumentsMap)
            }
        } catch (e: InvocationTargetException) {
            val targetException = e.targetException
            context(classLoader) {
                elideSystemStackTracePart(targetException)
            }

            userReadableError(targetException.stackTraceToString())
        }
    }

    private val logger = LoggerFactory.getLogger(javaClass)
}

/**
 * We do not include the "system" part of the stack trace, only the user-code part.
 */
context(userCodeClassLoader: ClassLoader)
private fun elideSystemStackTracePart(throwable: Throwable) {
    val stackTrace = throwable.stackTrace.toList()

    // Remove the "system" part of the stacktrace - it is of no interest to the user.
    throwable.stackTrace = stackTrace.subList(
        fromIndex = 0,
        toIndex = stackTrace.indexOfLast { it.classLoaderName == userCodeClassLoader.name } + 1,
    ).map { it.withoutClassloaderName() }.toTypedArray()

    throwable.cause?.let { elideSystemStackTracePart(it) }
}

private fun StackTraceElement.withoutClassloaderName() = StackTraceElement(
    /* classLoaderName = */ null,  // remove the classloader name
    /* moduleName = */ moduleName,
    /* moduleVersion = */ moduleVersion,
    /* declaringClass = */ className,
    /* methodName = */ methodName,
    /* fileName = */ fileName,
    /* lineNumber = */lineNumber,
)