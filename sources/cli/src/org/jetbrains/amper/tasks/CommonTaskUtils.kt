/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.tasks.jvm.JvmCompileTask
import java.nio.file.Path

object CommonTaskUtils {
    // TODO this not how classpath should be built, it does not preserve order
    //  also will fail on conflicting dependencies
    //  also it depends on task hierarchy, which could be different from classpath
    //  but for demo it's fine
    //  I suggest to return to this task after our own dependency resolution engine
    fun buildRuntimeClasspath(compileTaskResult: JvmCompileTask.Result): List<Path> {
        val result = mutableListOf<Path>()
        buildRuntimeClasspath(compileTaskResult, result)
        return result
    }

    private fun buildRuntimeClasspath(compileTaskResult: JvmCompileTask.Result, result: MutableList<Path>) {
        val externalClasspath =
            compileTaskResult.dependencies.filterIsInstance<ResolveExternalDependenciesTask.Result>()
                .flatMap { it.runtimeClasspath }
        for (path in externalClasspath) {
            if (!result.contains(path)) {
                result.add(path)
            }
        }

        for (depCompileResult in compileTaskResult.dependencies.filterIsInstance<JvmCompileTask.Result>()) {
            buildRuntimeClasspath(depCompileResult, result)
        }

        result.add(compileTaskResult.classesOutputRoot)
    }

    fun getRuntimeClasses(compileTaskResult: JvmCompileTask.Result): List<Path> {
        val arrayDeque = ArrayDeque<JvmCompileTask.Result>()
        arrayDeque.add(compileTaskResult)
        return buildList {
            while (arrayDeque.isNotEmpty()) {
                val current = arrayDeque.removeFirst()
                add(current.classesOutputRoot)

                for (depCompileResult in current.dependencies.filterIsInstance<JvmCompileTask.Result>()) {
                    arrayDeque.add(depCompileResult)
                }
            }
        }
    }

    fun Iterable<Fragment>.userReadableList() = map { it.name }.sorted().joinToString(" ")
}
