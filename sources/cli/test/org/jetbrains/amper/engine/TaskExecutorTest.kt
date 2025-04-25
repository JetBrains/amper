/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.jetbrains.amper.engine.TaskGraphBuilder
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.TaskResult
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration.Companion.seconds

class TaskExecutorTest {

    @Test
    fun simpleTaskDependencies() = runTest {
        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A"), listOf(TaskName("B")))
        builder.registerTask(TestTask("B"), listOf(TaskName("C")))
        builder.registerTask(TestTask("C"), listOf(TaskName("D")))
        builder.registerTask(TestTask("D"))
        val graph = builder.build()

        val executor = TaskExecutor(graph, TaskExecutor.Mode.GREEDY)

        executed.clear()
        executor.run(setOf(TaskName("A")))
        assertEquals(listOf("D", "C", "B", "A"), executed)

        executed.clear()
        executor.run(setOf(TaskName("B")))
        assertEquals(listOf("D", "C", "B"), executed)

        executed.clear()
        executor.run(setOf(TaskName("C")))
        assertEquals(listOf("D", "C"), executed)
    }

    @Test
    fun diamondTaskDependencies() = runTest {
        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A"), listOf(TaskName("B"), TaskName("C")))
        builder.registerTask(TestTask("B"), listOf(TaskName("D")))
        builder.registerTask(TestTask("C"), listOf(TaskName("D")))
        builder.registerTask(TestTask("D"))
        val graph = builder.build()

        val executor = TaskExecutor(graph, TaskExecutor.Mode.GREEDY)
        executor.run(setOf(TaskName("A")))

        if (executed != listOf("D", "B", "C", "A") && executed != listOf("D", "C", "B", "A")) {
            fail("Wrong execution order: $executed")
        }
    }

    @Test
    fun complexTaskDependencies() = runTest {
        executed.clear()

        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A"), dependsOn = listOf(TaskName("B")))
        builder.registerTask(TestTask("B"), dependsOn = listOf(TaskName("C"), TaskName("E")))
        builder.registerTask(TestTask("C"), dependsOn = listOf(TaskName("D"), TaskName("E")))
        builder.registerTask(TestTask("D"), dependsOn = listOf(TaskName("F")))
        builder.registerTask(TestTask("E"), dependsOn = listOf(TaskName("F")))
        builder.registerTask(TestTask("F"))
        val graph = builder.build()

        val executor = TaskExecutor(graph, TaskExecutor.Mode.FAIL_FAST)
        executor.run(setOf(TaskName("A")))

        if (executed != listOf("F", "E", "D", "C", "B", "A") && executed != listOf("F", "D", "E", "C", "B", "A")) {
            fail("Wrong execution order: $executed")
        }
    }

    @Test
    fun failedTaskCancelsDependentChain() = runTest {
        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A"), listOf(TaskName("B")))
        builder.registerTask(TestTask("B"), listOf(TaskName("C")))
        builder.registerTask(TestTask("C") { error("test failure") }, listOf(TaskName("D")))
        builder.registerTask(TestTask("D"))
        val graph = builder.build()

        val executor = TaskExecutor(graph, TaskExecutor.Mode.GREEDY)
        val result = executor.run(setOf(TaskName("A")))

        assertIs<ExecutionResult.Success>(result.getValue(TaskName("D")))

        val resultC = result.getValue(TaskName("C"))
        assertIs<ExecutionResult.Failure>(resultC)
        assertIs<IllegalStateException>(resultC.exception)

        val resultB = result.getValue(TaskName("B"))
        assertIs<ExecutionResult.DependencyFailed>(resultB)
        assertEquals(setOf(resultC), resultB.unsuccessfulDependencies)
        assertEquals(setOf(resultC), resultB.transitiveFailures)

        val resultA = result.getValue(TaskName("A"))
        assertIs<ExecutionResult.DependencyFailed>(resultA)
        assertEquals(setOf(resultB), resultA.unsuccessfulDependencies)
        assertEquals(setOf(resultC), resultA.transitiveFailures)
    }

    @Test
    fun executesAllPossibleTasksOnTaskFailureInGreedyMode() = runTest {
        // Given the task graph dependencies:
        // A -> B
        // A -> C
        // C -> D
        // if D fails, B should be still executed

        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A"), listOf(TaskName("B"), TaskName("C")))
        builder.registerTask(TestTask("B") { delay(500) }) // add enough time for D to cancel execution of itself
        builder.registerTask(TestTask("C"), listOf(TaskName("D")))
        builder.registerTask(TestTask("D") { error("throw") })
        val graph = builder.build()

        val executor = TaskExecutor(graph, TaskExecutor.Mode.GREEDY)
        val result = executor.run(setOf(TaskName("A")))

        val resultD = result.getValue(TaskName("D"))
        assertIs<ExecutionResult.Failure>(resultD)

        val resultC = result.getValue(TaskName("C"))
        assertIs<ExecutionResult.DependencyFailed>(resultC)
        assertEquals(setOf(resultD), resultC.unsuccessfulDependencies)
        assertEquals(setOf(resultD), resultC.transitiveFailures)

        val resultB = result.getValue(TaskName("B"))
        assertIs<ExecutionResult.Success>(resultB)

        val resultA = result.getValue(TaskName("A"))
        assertIs<ExecutionResult.DependencyFailed>(resultA)
        assertEquals(setOf(resultC), resultA.unsuccessfulDependencies)
        assertEquals(setOf(resultD), resultA.transitiveFailures)
    }

    @Test
    fun stopsOnFirstTaskFailureInFailFastMode() = runTest {
        // Given the task graph dependencies:
        // A -> B
        // A -> C
        // C -> D
        // if D fails, B should not be still executed because of FAIL_FAST mode

        val builder = TaskGraphBuilder()
        builder.registerTask(TestTask("A"), listOf(TaskName("B"), TaskName("C")))
        builder.registerTask(TestTask("B") { delay(500) }) // add enough time for D to cancel execution of itself
        builder.registerTask(TestTask("C"), listOf(TaskName("D")))
        builder.registerTask(TestTask("D") { error("throw") })
        val graph = builder.build()
        val executor = TaskExecutor(graph, TaskExecutor.Mode.FAIL_FAST)
        val result = assertFailsWith(TaskExecutor.TaskExecutionFailed::class) {
            executor.run(setOf(TaskName("A")))
        }
        assertEquals("Task 'D' failed: java.lang.IllegalStateException: throw", result.message)
    }

    @Test
    fun failsOnTaskDependencyCycle() = runTest {
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
            executor.run(setOf(TaskName("D")))
        }
        val expectedError =  """
            Found a cycle in task execution graph:
            D
             -> C
             -> B
             -> A
             -> D
        """.trimIndent()
        assertEquals(expectedError, result.message)
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
    private val runningTasksCount = AtomicInteger(0)
    private val maxParallelTasksCount = AtomicInteger(0)

    private inner class TestTask(
        val name: String,
        val body: suspend () -> Unit = {},
    ): Task {
        override val taskName: TaskName
            get() = TaskName(name)

        override suspend fun run(
            dependenciesResult: List<TaskResult>,
            executionContext: TaskGraphExecutionContext,
        ): TaskResult {
            val currentTasksCount = runningTasksCount.incrementAndGet()
            maxParallelTasksCount.updateAndGet { max -> max(max, currentTasksCount) }
            try {
                synchronized(executed) {
                    executed.add(name)
                }
                body()
                return TestTaskResult(taskName)
            } finally {
                runningTasksCount.decrementAndGet()
            }
        }
    }

    private class TestTaskResult(val taskName: TaskName) : TaskResult
}
