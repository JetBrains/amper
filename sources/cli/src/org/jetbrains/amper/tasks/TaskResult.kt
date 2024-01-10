/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import kotlin.reflect.KClass

interface TaskResult {
    val dependencies: List<TaskResult>

    companion object {
        inline fun <reified R: TaskResult> TaskResult.walkDependenciesRecursively(): List<R> {
            val result = mutableListOf<R>()
            collectDependenciesRecursively(R::class, result)
            return result
        }

        fun <R: TaskResult> TaskResult.collectDependenciesRecursively(type: KClass<R>, result: MutableList<R>) {
            for (dependency in dependencies) {
                if (type.isInstance(dependency)) {
                    @Suppress("UNCHECKED_CAST")
                    result.add(dependency as R)
                }

                dependency.collectDependenciesRecursively(type, result)
            }
        }
    }
}
