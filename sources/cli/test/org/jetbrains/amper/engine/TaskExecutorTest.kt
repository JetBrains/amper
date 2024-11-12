/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.jetbrains.amper.cli.TaskGraphBuilder
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

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
            executor.run(setOf(TaskName("A")))
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
        builder.registerTask(TestTask("A") { error("throw") })
        builder.registerTask(TestTask("B"), listOf(TaskName("A")))
        builder.registerTask(TestTask("C") { delay(500) }) // add enough time for A to cancel execution of itself
        builder.registerTask(TestTask("D"), listOf(TaskName("B"), TaskName("C")))
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.GREEDY)
        val result = runBlocking {
            executor.run(setOf(TaskName("D")))
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
        builder.registerTask(TestTask("A") { error("throw") })
        builder.registerTask(TestTask("B"), listOf(TaskName("A")))
        builder.registerTask(TestTask("C") { delay(500) }) // add enough time for A to cancel execution of itself
        builder.registerTask(TestTask("D"), listOf(TaskName("B"), TaskName("C")))
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.FAIL_FAST)
        val result = assertFailsWith(TaskExecutor.TaskExecutionFailed::class) {
            runBlocking {
                executor.run(setOf(TaskName("D")))
            }
        }
        assertEquals("Task 'A' failed: java.lang.IllegalStateException: throw", result.message)
    }

    @Test
    fun executionCycle() {
        // Given the task graph dependencies:
        // D -> C
        // C -> B
        // B -> A
        // A -> D
        // it should fail

        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("D"), listOf(TaskName("C")))
        builder.registerTask(TestTask("C"), listOf(TaskName("B")))
        builder.registerTask(TestTask("B"), listOf(TaskName("A")))
        builder.registerTask(TestTask("A"), listOf(TaskName("D")))
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.FAIL_FAST)
        val result = assertFailsWith(IllegalStateException::class) {
            runBlocking {
                executor.run(setOf(TaskName("D")))
            }
        }
        assertEquals("Found a cycle in task execution graph:\n" +
                "D -> C -> B -> A -> D", result.message)
    }

    @Test
    fun rootTasksExecuteInParallel() = runTest {
        val builder = TaskGraphBuilder()
        val parallelTasks = setOf("A", "B", "C")
        parallelTasks.forEach { name ->
            builder.registerTask(TestTask(name) {
                withTimeout(10.seconds) {
                    while (maxParallelTasksCount.get() < parallelTasks.size) {
                        delay(10)
                    }
                }
            })
        }
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.FAIL_FAST)
        executor.run(setOf(TaskName("A"), TaskName("B"), TaskName("C")))
        assertEquals(3, maxParallelTasksCount.get())
    }

    private val executed = mutableListOf<String>()
    private val tasksCount = AtomicInteger(0)
    private val maxParallelTasksCount = AtomicInteger(0)
    private inner class TestTask(
        val name: String,
        val body: suspend () -> Unit = {},
    ): Task {
        override val taskName: TaskName
            get() = TaskName(name)

        override suspend fun run(dependenciesResult: List<TaskResult>): TaskResult {
            val currentTasksCount = tasksCount.incrementAndGet()
            maxParallelTasksCount.updateAndGet { max -> max(max, currentTasksCount) }
            try {
                synchronized(executed) {
                    executed.add(name)
                }
                body()
                return TestTaskResult(taskName)
            } finally {
                tasksCount.decrementAndGet()
            }
        }
    }

    private class TestTaskResult(val taskName: TaskName) : TaskResult
}
