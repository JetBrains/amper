/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.animation.animation
import com.github.ajalt.mordant.table.horizontalLayout
import com.github.ajalt.mordant.table.verticalLayout
import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.engine.TaskProgressListener
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Collections.swap

@OptIn(FlowPreview::class)
class TaskProgressRenderer(private val terminal: Terminal, private val coroutineScope: CoroutineScope) : TaskProgressListener {
    private data class ThreadState(val name: String?, val startTime: Instant, val elapsedSeconds: Long)

    private val maxTasksOnScreen
        get() = terminal.info.height / 3

    private val updateFlow: MutableStateFlow<List<ThreadState>> by lazy {
        val flow = MutableStateFlow(emptyList<ThreadState>())

        coroutineScope.launch(Dispatchers.IO) {
            val animation = terminal.animation<List<ThreadState>> { tasks ->
                verticalLayout {
                    cell("")
//                    cell(HorizontalRule())

                    for (threadState in tasks.take(maxTasksOnScreen)) {
                        cell(horizontalLayout {
                            cell(">" ) {
                                style = terminal.theme.muted
                            }
                            if (threadState.name == null) {
                                cell("IDLE") {
                                    style = terminal.theme.muted
                                }
                            } else {
                                cell(threadState.name) {
                                    style = terminal.theme.info
                                }

                                if (threadState.elapsedSeconds >= 1) {
                                    cell("${threadState.elapsedSeconds}s") {
                                        style = terminal.theme.muted
                                    }
                                }
                            }
                        })
                    }
                    if (tasks.size > maxTasksOnScreen) {
                        cell("(+${tasks.size - maxTasksOnScreen} more)")
                    }
                    cell("")
                }
            }

            coroutineScope.launch(Dispatchers.IO) {
                while (true) {
                    updateState()
                    delay(100)
                }
            }

            val mutex = Mutex()
            try {
                flow.debounce(30).collectLatest { snapshot ->
                    // animation code is single-threaded
                    mutex.withLock {
                        animation.update(snapshot)
                    }
                }
            } finally {
                animation.clear()
            }
        }

        flow
    }

    private fun updateState() {
        updateFlow.update { old ->
            old.map { it.copy(elapsedSeconds = ChronoUnit.SECONDS.between(it.startTime, Instant.now())) }
        }
    }

    private fun trimOverflow(mutable: MutableList<ThreadState>) {
        val maxTasksOnScreen = maxTasksOnScreen
        if (mutable.size <= maxTasksOnScreen) return

        // try to fill idle positions on screen with tasks not on screen
        for (i in 0 until maxTasksOnScreen) {
            if (mutable[i].name == null) {
                // the current position is idle, let's try to fill it
                // not very effective, but good enough
                var swapped = false
                for (j in mutable.size - 1 downTo  maxTasksOnScreen) {
                    if (mutable[j].name != null) {
                        swap(mutable, i, j)
                        swapped = true
                        break
                    }
                }
                if (!swapped) {
                    // nothing to swap => can't fill more
                    break
                }
            }
        }

        // trim idle positions on the end
        while (mutable.isNotEmpty() && mutable[mutable.lastIndex].name == null) {
            mutable.removeLast()
        }
    }

    override fun taskStarted(taskName: TaskName): TaskProgressListener.TaskProgressCookie {
        val job = coroutineScope.launch(Dispatchers.IO) {
            val newThreadState = ThreadState(taskName.name, startTime = Instant.now(), elapsedSeconds = 0)
            delay(200)
            updateFlow.update { current ->
                val mutable = current.toMutableList()
                val emptyIndex = current
                    .withIndex()
                    .filter { it.value.name == null }
                    .minByOrNull { it.value.startTime }
                if (emptyIndex != null) {
                    mutable[emptyIndex.index] = newThreadState
                } else {
                    mutable.add(newThreadState)
                }
                trimOverflow(mutable)
                mutable
            }
        }

        return object : TaskProgressListener.TaskProgressCookie {
            override fun close() {
                job.cancel()

                updateFlow.update { current ->
                    val mutable = current.toMutableList()
                    val taskIndex = mutable.indexOfFirst { it.name == taskName.name }
                    if (taskIndex >= 0) {
                        mutable[taskIndex] = ThreadState(null, startTime = Instant.now(), elapsedSeconds = 0)
                    }
                    trimOverflow(mutable)
                    mutable
                }
            }
        }
    }
}
