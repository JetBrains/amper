/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.nio.file.Path
import java.util.*


fun startProcessWithStdoutStderrFlows(
    command: List<String>,
    workingDir: Path,
    environment: Map<String, String>
): FlowStdoutStderrProcess {
    val process = ProcessBuilder(command)
        .directory(workingDir.toFile())
        .also { it.environment().putAll(environment) }
        .start()
    return FlowStdoutStderrProcess(process)
}

class FlowStdoutStderrProcess(private val process: Process) {
    fun stop() {
        process.destroy()
    }

    val stdout: Flow<String>
        get() {
            val sc = Scanner(process.inputStream)
            return flow {
                while (sc.hasNextLine() && process.isAlive) {
                    emit(sc.nextLine())
                }
            }
        }

    val stderr: Flow<String>
        get() {
            val sc = Scanner(process.errorStream)
            return flow {
                while (sc.hasNextLine() && process.isAlive) {
                    emit(sc.nextLine())
                }
            }
        }
}