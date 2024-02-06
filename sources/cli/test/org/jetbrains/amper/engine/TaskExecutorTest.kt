/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.tasks.TaskResult
import kotlin.test.Test
import kotlin.test.fail

class TaskExecutorTest {
    @Test
    fun diamondTaskDependencies() {
        val executed = mutableListOf<String>()

        class TestTask(val name: String): Task {
            override val taskName: TaskName
                get() = TaskName(name)

            override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
                synchronized(executed) {
                    executed.add(name)
                }

                return object : TaskResult {
                    override val dependencies: List<TaskResult> = dependenciesResult
                }
            }
        }

        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("D"))
        builder.registerTask(TestTask("B"), listOf(TaskName("D")))
        builder.registerTask(TestTask("C"), listOf(TaskName("D")))
        builder.registerTask(TestTask("A"), listOf(TaskName("B"), TaskName("C")))
        val graph = builder.build()
        val executor = TaskExecutor(graph)
        runBlocking {
            executor.run(listOf(TaskName("A")))
        }
        if (executed != listOf("D", "B", "C", "A") && executed != listOf("D", "C", "B", "A")) {
            fail("Wrong execution order: $executed")
        }
    }
}