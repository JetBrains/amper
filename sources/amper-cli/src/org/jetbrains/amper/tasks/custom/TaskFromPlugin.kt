/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.api.SchemaNode
import org.jetbrains.amper.frontend.api.UnstableSchemaApi
import org.jetbrains.amper.frontend.api.toStringRepresentation
import org.jetbrains.amper.frontend.plugins.ExtensionSchemaNode
import org.jetbrains.amper.frontend.plugins.GeneratedPathKind
import org.jetbrains.amper.frontend.plugins.TaskFromPluginDescription
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.api.Artifact
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.tasks.artifacts.api.ArtifactTask
import org.jetbrains.amper.tasks.artifacts.api.ArtifactType
import org.jetbrains.amper.tasks.artifacts.api.Quantifier
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.net.URLClassLoader
import java.nio.file.Path
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.kotlinFunction

class TaskFromPlugin(
    override val taskName: TaskName,
    val module: AmperModule,
    val description: TaskFromPluginDescription,
    val buildOutputRoot: AmperBuildOutputRoot,
    val terminal: Terminal,
) : ArtifactTask {

    interface ExternalTaskArtifact : Artifact

    class ExternalTaskRawArtifact(
        override val path: Path,
    ) : ExternalTaskArtifact

    class ExternalTaskGeneratedKotlinSourcesArtifact(
        buildOutputRoot: AmperBuildOutputRoot,
        fragment: Fragment,
        path: Path,
    ) : KotlinJavaSourceDirArtifact(buildOutputRoot, fragment, path), ExternalTaskArtifact

    override val consumes: List<ArtifactSelector<*, *>> = description.inputs
        .map { it }
        .map { inputPath ->
            ArtifactSelector(
                type = ArtifactType(ExternalTaskArtifact::class),
                predicate = { it.path == inputPath },
                description = "with path '${inputPath}'",
                quantifier = Quantifier.Single,
            )
        }

    override val produces: List<Artifact> = description.outputs
        .map { (output, mark) ->
            when (mark?.kind) {
                null -> ExternalTaskRawArtifact(output)
                GeneratedPathKind.KotlinSources -> ExternalTaskGeneratedKotlinSourcesArtifact(
                    buildOutputRoot = buildOutputRoot,
                    fragment = mark.associateWith,
                    path = output,
                )
                GeneratedPathKind.JavaSources,
                GeneratedPathKind.JvmResources,
                    -> throw UnsupportedOperationException("${mark.kind} is not yet supported!")
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
            .first { it.module == description.codeSource }

        // TODO: Cache the classloader per plugin?
        val classLoader = URLClassLoader(
            taskName.toString(),
            taskCode.jvmRuntimeClasspath.map { it.toUri().toURL() }.toTypedArray(),
            javaClass.classLoader,
        )

        val actionFacade = classLoader.loadClass(description.actionClassJvmName)
        val actionMethod = actionFacade.methods.first { it.name == description.actionFunctionJvmName }.kotlinFunction!!
        val argumentsMap = description.actionArguments.mapKeys { (name, _) ->
            actionMethod.parameters.first { it.name == name }
        }.mapValues { (_, value) ->
            when (value) {
                is ExtensionSchemaNode -> createProxy(classLoader, value)
                is SchemaNode -> TODO("Passing internal schema objects to external tasks is not yet supported")
                is Path -> value
                else -> value
            }
        }

        try {
            actionMethod.callBy(argumentsMap)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }

        return EmptyTaskResult
    }

    private fun createProxy(
        classLoader: ClassLoader,
        value: ExtensionSchemaNode,
    ): Any {
        val interfaceClass = classLoader.loadClass(checkNotNull(value.interfaceName) {
            "Not reached: the schema object has no plugin counterpart"
        })
        val handler = InvocationHandler { proxy: Any, method: Method, args: Array<out Any?>? ->
            @OptIn(UnstableSchemaApi::class)
            when (method.name) {
                "toString" -> value.toStringRepresentation()
                "hashCode" -> value.hashCode()
                "equals" -> args?.get(0) === proxy
                else -> {
                    val property = method.declaringClass.kotlin.memberProperties
                        .first { it.getter.javaMethod == method }
                    val value = value.valueHolders[property.name]?.value
                    when {
                        value == null -> null
                        method.returnType.isEnum -> {
                            check(value is String)
                            method.returnType.enumConstants.filterIsInstance<Enum<*>>().first { it.name == value }
                        }
                        else -> value
                    }
                }
            }
        }
        return Proxy.newProxyInstance(classLoader, arrayOf(interfaceClass), handler)
    }
}