/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.custom

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.cli.AmperProjectTempRoot
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.engine.TaskGraphExecutionContext
import org.jetbrains.amper.frontend.AddToModuleRootsFromCustomTask
import org.jetbrains.amper.frontend.CompositeString
import org.jetbrains.amper.frontend.CompositeStringPart
import org.jetbrains.amper.frontend.CustomTaskDescription
import org.jetbrains.amper.frontend.KnownCurrentTaskProperty
import org.jetbrains.amper.frontend.KnownModuleProperty
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.PublishArtifactFromCustomTask
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.jdk.provisioning.JdkDownloader
import org.jetbrains.amper.jvm.getEffectiveJvmMainClass
import org.jetbrains.amper.processes.PrintToTerminalProcessOutputListener
import org.jetbrains.amper.processes.runJava
import org.jetbrains.amper.tasks.TaskOutputRoot
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.JvmResourcesDirArtifact
import org.jetbrains.amper.tasks.artifacts.KotlinJavaSourceDirArtifact
import org.jetbrains.amper.tasks.artifacts.api.Artifact
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.tasks.artifacts.api.ArtifactTask
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isRegularFile
import kotlin.io.path.pathString

internal class CustomTask(
    private val custom: CustomTaskDescription,
    private val taskOutputRoot: TaskOutputRoot,
    private val userCacheRoot: AmperUserCacheRoot,
    private val tempRoot: AmperProjectTempRoot,
    private val terminal: Terminal,
    buildOutputRoot: AmperBuildOutputRoot,
): ArtifactTask {
    override val taskName: TaskName
        get() = custom.name

    override val consumes get() = emptyList<Nothing>()

    override val produces: List<Artifact> = custom.addToModuleRootsFromCustomTask
        .map {
            when(it.type) {
                AddToModuleRootsFromCustomTask.Type.RESOURCES -> JvmResourcesDirArtifact(
                    buildOutputRoot,
                    fragment = it.resolveFragment(),
                    conventionPath = it.resolvePath(),
                )
                AddToModuleRootsFromCustomTask.Type.SOURCES -> KotlinJavaSourceDirArtifact(
                    buildOutputRoot,
                    fragment = it.resolveFragment(),
                    conventionPath = it.resolvePath(),
                )
            }
        }

    override fun injectConsumes(artifacts: Map<ArtifactSelector<*, *>, List<Artifact>>) = Unit

    override suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult {
        // do not clean custom task output
        // it's a responsibility of the custom task itself right now
        // in the future, we want to automatically track what's accessed by custom task and
        // call it only if something changed on subsequent runs
        taskOutputRoot.path.createDirectories()

        val codeModule = custom.customTaskCodeModule

        check(codeModule.type == ProductType.JVM_APP) {
            "Custom task module '${codeModule.userReadableName}' should have 'jvm/app' type"
        }

        val jvmRuntimeClasspathTask = dependenciesResult.filterIsInstance<JvmRuntimeClasspathTask.Result>()
            .singleOrNull { it.module == codeModule && !it.isTest}
            ?: error("${JvmRuntimeClasspathTask::class.simpleName} result for module '${codeModule.userReadableName}' is not found in dependencies")

        val jdk = JdkDownloader.getJdk(userCacheRoot)

        val workingDir = custom.source.parent

        // TODO add a span?

        val result = jdk.runJava(
            workingDir = workingDir,
            mainClass = codeModule.fragments.getEffectiveJvmMainClass(),
            classpath = jvmRuntimeClasspathTask.jvmRuntimeClasspath,
            programArgs = custom.programArguments.map { interpolateString(it, dependenciesResult) },
            jvmArgs = custom.jvmArguments.map { interpolateString(it, dependenciesResult) },
            environment = custom.environmentVariables.mapValues { interpolateString(it.value, dependenciesResult) },
            outputListener = PrintToTerminalProcessOutputListener(terminal),
            tempRoot = tempRoot,
        )

        if (result.exitCode != 0) {
            val message = "Custom task '${codeModule.userReadableName}' exited with exit code ${result.exitCode}" +
                    (if (result.stderr.isNotEmpty()) "\nSTDERR:\n${result.stderr}\n" else "") +
                    (if (result.stdout.isNotEmpty()) "\nSTDOUT:\n${result.stdout}\n" else "")
            userReadableError(message)
        }

        custom.publishArtifacts.forEach { publish ->
            // TODO wildcard matching support?
            val path = taskOutputRoot.path.resolve(publish.pathWildcard).normalize()
            if (!path.startsWith(taskOutputRoot.path)) {
                // TODO Move to frontend and BuildProblems?
                userReadableError("Task output relative path '${publish.pathWildcard}'" +
                        "must be under task output '${taskOutputRoot.path}', but got: $path")
            }

            if (!path.isRegularFile()) {
                userReadableError("After running a custom task '${custom.name.name}' output file or folder '$path'" +
                        "is not found, but required for publishing")
            }
        }

        return Result(
            outputDirectory = taskOutputRoot.path,
            artifactsToPublish = custom.publishArtifacts,
        )
    }

    private fun AddToModuleRootsFromCustomTask.resolvePath(): Path {
        val path = taskOutputRoot.path.resolve(taskOutputRelativePath).normalize()
        if (!path.startsWith(taskOutputRoot.path)) {
            // TODO Move to frontend and BuildProblems?
            userReadableError("Task output relative path '${taskOutputRelativePath}'" +
                    "must be under task output '${taskOutputRoot.path}', but got: $path")
        }
        return path
    }

    private fun AddToModuleRootsFromCustomTask.resolveFragment(): LeafFragment {
        val fragment = custom.module.leafFragments.find { it.platform == platform }
        if (fragment == null) {
            userReadableError("Custom task '${custom.name.name}' cannot add sources to platform " +
                    "'${platform.pretty}' because it's not targeted by module " +
                    "'${custom.module.userReadableName}'")
        }
        return fragment
    }

    private fun interpolateString(compositeString: CompositeString, dependencies: List<TaskResult>): String {
        val stringChunks = compositeString.parts.map { part ->
            when (part) {
                is CompositeStringPart.Literal -> part.value
                is CompositeStringPart.ModulePropertyReference -> {
                    when (part.property) {
                        KnownModuleProperty.VERSION -> {
                            part.referencedModule.rootFragment.settings.publishing?.version
                                ?: userReadableError("Version is not defined for module '${part.referencedModule.userReadableName}', but it's referenced in custom task '${taskName.name}'")
                        }
                        KnownModuleProperty.NAME -> part.referencedModule.userReadableName
                        KnownModuleProperty.JVM_RUNTIME_CLASSPATH -> {
                            val jvmRuntimeClasspathResult = dependencies
                                .filterIsInstance<JvmRuntimeClasspathTask.Result>()
                                .single { it.module == part.referencedModule && !it.isTest }
                            jvmRuntimeClasspathResult.jvmRuntimeClasspath.joinToString(File.pathSeparator) { it.pathString }
                        }
                    }
                }

                is CompositeStringPart.CurrentTaskProperty -> {
                    when (part.property) {
                        KnownCurrentTaskProperty.OUTPUT_DIRECTORY -> {
                            taskOutputRoot.path.pathString
                        }
                    }
                }
/*
                is CompositeStringPart.TaskPropertyReference -> {
                    val taskResult = dependencies.firstOrNull { it.taskName == part.taskName }
                    // This is an internal error since Amper should automatically add this dependency
                        ?: error("Task '${part.taskName.name}' is not in dependencies of task '${taskName.name}', " +
                                "but referenced by '${part.originalReferenceText}' in task parameters")

                    val value = taskResult.outputProperties[part.propertyName.propertyName]
                        ?: userReadableError("Task output property '${part.propertyName.propertyName}' is not found in " +
                                "task '${part.taskName}' results")

                    value
                }
*/
            }
        }
        return stringChunks.joinToString(separator = "")
    }

    class Result(
        val outputDirectory: Path,
        val artifactsToPublish: List<PublishArtifactFromCustomTask>,
    ): TaskResult

    private val logger = LoggerFactory.getLogger(javaClass)
}
