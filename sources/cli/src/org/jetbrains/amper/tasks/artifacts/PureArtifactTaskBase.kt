/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.api.Artifact
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.File
import java.io.Serializable
import java.nio.file.Path
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo
import kotlin.reflect.KProperty

/**
 * "Pure" artifact-based task. Such a task features the following traits:
 * - automatically cacheable based on input/output artifacts and extra inputs (using [ExecuteOnChangedInputs] internally)
 * = automatically cleans its output before the action
 *
 * If one doesn't want the restrictions imposed by this class, subclass the [ArtifactTaskBase] instead.
 */
abstract class PureArtifactTaskBase(
    buildOutputRoot: AmperBuildOutputRoot,
) : ArtifactTaskBase(), Serializable {
    private val executeOnChangedInputs = ExecuteOnChangedInputs(buildOutputRoot)
    private val extraInputs = mutableMapOf<String, String>()
    private lateinit var inputPaths: List<Path>

    override val taskName: TaskName by lazy {
        val output = produces.single()
        val path = output.path.relativeTo(buildOutputRoot.path)
        TaskName.fromHierarchy(listOf(path.pathString))
    }

    private fun extraInput(key: String, value: String) {
        checkConfigurationNotCompleted()
        check(extraInputs.put(key, value) == null) { "Key $key is already present" }
    }

    protected inner class ExtraInputDelegate<T>(
        private val value: T,
        private val serializer: KSerializer<T>,
    ) {
        operator fun provideDelegate(thisRef: PureArtifactTaskBase, property: KProperty<*>) = apply {
            thisRef.extraInput(property.name, Json.encodeToString(serializer, value))
        }

        @Suppress("unused")
        operator fun getValue(thisRef: Any, prop: Any) = value
    }

    protected inline fun <reified T> extraInput(value: T) = ExtraInputDelegate(value, serializer())

    final override fun injectConsumes(artifacts: Map<ArtifactSelector<*, *>, List<Artifact>>) {
        super.injectConsumes(artifacts)
        inputPaths = artifacts.values.asSequence()
            .flatten().distinct().mapNotNull { it.path.takeIf(Path::exists) }.toList()
    }

    abstract suspend fun run()

    final override suspend fun run(
        dependenciesResult: List<TaskResult>
    ): TaskResult {
        executeOnChangedInputs.execute(
            id = taskName.name,
            configuration = extraInputs +
                    ("%outputs%" to produces.joinToString(File.pathSeparator) { it.path.pathString }),
            inputs = inputPaths,
        ) {
            for (path in produces.map { it.path }) {
                path.createParentDirectories()
                path.deleteRecursively()
            }
            run()
            ExecuteOnChangedInputs.ExecutionResult(
                outputs = produces.map { it.path }.filter { it.exists() },
            )
        }

        return EmptyTaskResult
    }
}