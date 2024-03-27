/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.tasks.TaskResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class TaskExecutorTest {
    @Test
    fun diamondTaskDependencies() {
        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("D"))
        builder.registerTask(TestTask("B"), listOf(TaskName("D")))
        builder.registerTask(TestTask("C"), listOf(TaskName("D")))
        builder.registerTask(TestTask("A"), listOf(TaskName("B"), TaskName("C")))
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.GREEDY)
        runBlocking {
            executor.run(listOf(TaskName("A")))
        }
        if (executed != listOf("D", "B", "C", "A") && executed != listOf("D", "C", "B", "A")) {
            fail("Wrong execution order: $executed")
        }
    }

    @Test
    fun executionExecutesAllPossibleTasksOnTaskFailureInGreedyMode() {
        // Given the task graph dependencies:
        // D -> C
        // D -> B
        // B -> A
        // if A fails, C should be still executed

        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A", throwException = true))
        builder.registerTask(TestTask("B"), listOf(TaskName("A")))
        builder.registerTask(TestTask("C", delayMs = 500)) // add enough time for A to cancel execution of itself
        builder.registerTask(TestTask("D"), listOf(TaskName("B"), TaskName("C")))
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.GREEDY)
        val result = runBlocking {
            executor.run(listOf(TaskName("D")))
        }
        assertEquals("C", (result.getValue(TaskName("C")).getOrThrow() as TestTaskResult).taskName.name)
        assertTrue(result.getValue(TaskName("A")).exceptionOrNull() is IllegalStateException)
        assertTrue(result.getValue(TaskName("B")).exceptionOrNull() is CancellationException)
        assertTrue(result.getValue(TaskName("D")).exceptionOrNull() is CancellationException)
    }

    @Test
    fun executionTerminatesOnTaskFailureInFailFastMode() {
        // Given the task graph dependencies:
        // D -> C
        // D -> B
        // B -> A
        // if A fails, C should not be still executed because of FAIL_FAST mode

        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A", throwException = true))
        builder.registerTask(TestTask("B"), listOf(TaskName("A")))
        builder.registerTask(TestTask("C", delayMs = 500)) // add enough time for A to cancel execution of itself
        builder.registerTask(TestTask("D"), listOf(TaskName("B"), TaskName("C")))
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.FAIL_FAST)
        val result = runBlocking {
            executor.run(listOf(TaskName("D")))
        }
        assertTrue(result.getValue(TaskName("C")).exceptionOrNull() is CancellationException)
        assertTrue(result.getValue(TaskName("A")).exceptionOrNull() is IllegalStateException)
        assertTrue(result.getValue(TaskName("B")).exceptionOrNull() is CancellationException)
        assertTrue(result.getValue(TaskName("D")).exceptionOrNull() is CancellationException)
    }

    private val executed = mutableListOf<String>()
    private inner class TestTask(val name: String, val delayMs: Long = 0, val throwException: Boolean = false): Task {
        override val taskName: TaskName
            get() = TaskName(name)

        override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
            synchronized(executed) {
                executed.add(name)
            }
            delay(delayMs)
            if (throwException) error("throw")
            return TestTaskResult(taskName, dependenciesResult)
        }
    }

    private class TestTaskResult(val taskName: TaskName, override val dependencies: List<TaskResult>) : TaskResult
}
