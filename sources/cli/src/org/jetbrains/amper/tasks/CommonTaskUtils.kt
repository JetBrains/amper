/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.Fragment
import java.nio.file.Path

object CommonTaskUtils {
    // TODO this not how classpath should be built, it does not preserve order
    //  also will fail on conflicting dependencies
    //  also it depends on task hierarchy, which could be different from classpath
    //  but for demo it's fine
    //  I suggest to return to this task after our own dependency resolution engine
    fun buildRuntimeClasspath(compileTaskResult: JvmCompileTask.TaskResult): List<Path> {
        val result = mutableListOf<Path>()
        buildRuntimeClasspath(compileTaskResult, result)
        return result
    }

    private fun buildRuntimeClasspath(compileTaskResult: JvmCompileTask.TaskResult, result: MutableList<Path>) {
        val externalClasspath =
            compileTaskResult.dependencies.filterIsInstance<ResolveExternalDependenciesTask.TaskResult>()
                .flatMap { it.runtimeClasspath }
        for (path in externalClasspath) {
            if (!result.contains(path)) {
                result.add(path)
            }
        }

        for (depCompileResult in compileTaskResult.dependencies.filterIsInstance<JvmCompileTask.TaskResult>()) {
            buildRuntimeClasspath(depCompileResult, result)
        }

        compileTaskResult.classesOutputRoot?.let { result.add(it) }
    }

    fun Iterable<Fragment>.userReadableList() = map { it.name }.sorted().joinToString(" ")
}
