/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.artifacts

import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.tasks.EmptyTaskResult
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.tasks.artifacts.api.Artifact
import org.jetbrains.amper.tasks.artifacts.api.ArtifactSelector
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.NotSerializableException
import java.io.ObjectOutputStream
import java.io.Serializable
import java.nio.file.Path
import java.util.*
import kotlin.io.path.createParentDirectories
import kotlin.io.path.deleteRecursively
import kotlin.io.path.exists
import kotlin.io.path.pathString
import kotlin.io.path.relativeTo

/**
 * "Pure" artifact-based task. Such a task features the following traits:
 * - automatically cacheable based on input/output artifacts (using [ExecuteOnChangedInputs] internally)
 * = automatically cleans its output before the action
 * - automatically tracks its additional configuration inputs via the serialization mechanism
 *
 * If one doesn't want the restrictions imposed by this class, subclass the [ArtifactTaskBase] instead.
 */
abstract class PureArtifactTaskBase(
    buildOutputRoot: AmperBuildOutputRoot,
) : ArtifactTaskBase(), Serializable {
    @Transient
    private val executeOnChangedInputs = ExecuteOnChangedInputs(buildOutputRoot)
    @Transient
    private lateinit var inputPaths: List<Path>

    @delegate:Transient
    override val taskName: TaskName by lazy {
        val output = produces.single()
        val path = output.path.relativeTo(buildOutputRoot.path)
        TaskName.fromHierarchy(listOf(path.pathString))
    }

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
            configuration = mapOf(
                "fingerprint" to generateConfigurationFingerprint(),
                "outputs" to produces.joinToString(File.pathSeparator) { it.path.pathString },
            ),
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

    private fun generateConfigurationFingerprint(): String {
        // The general idea here is to serialize the task object as an additional "input" so the
        // incremental engine tracks it. If any captured inputs change, then the task will be no up-to-date.
        val out = ByteArrayOutputStream()
        ObjectOutputStream(out).use {
            try {
                it.writeObject(this@PureArtifactTaskBase)
            } catch (e: NotSerializableException) {
                throw RuntimeException("$taskName: not serializable: ${e.message}", e)
            }
        }
        return Base64.getEncoder().encodeToString(out.toByteArray())
    }
}