/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:JvmName("TasksKt")

package org.jetbrains.amper.engine

import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import org.jetbrains.amper.util.BuildType

interface Task {

    val taskName: TaskName

    suspend fun run(dependenciesResult: List<TaskResult>, executionContext: TaskGraphExecutionContext): TaskResult
}

interface RunTask : Task {
    val platform: Platform
    val buildType: BuildType
    val module: AmperModule
}

interface PackageTask : Task {
    enum class Format(val value: String) {
        Jar("jar"),
        ExecutableJar("executable-jar"),
        Aab("aab"),
        DistZip("dist-zip");

        companion object {
            val formatStrings: Set<String> = Format.entries.map { it.value }.toSet()
            fun byValue(value: String): Format? = Format.entries.associateBy { it.value }[value]
        }
    }

    val platform: Platform
    val buildType: BuildType
    val format: Format
    val module: AmperModule
}

interface TestTask : Task {
    val platform: Platform
    val module: AmperModule
}

/**
 * A task attached to the 'build' command.
 */
interface BuildTask : Task {
    val module: AmperModule
    val isTest: Boolean
    val platform: Platform

    /**
     * If `null` then build type dimension is not applicable to the task.
     */
    val buildType: BuildType?
        get() = null
}

/**
 * Find a task dependency with a specified type.
 */
inline fun <reified T : TaskResult> List<TaskResult>.requireSingleDependency() =
    filterIsInstance<T>().firstOrNull() ?: error("Expected to have single \"${T::class.simpleName}\" as a dependency")