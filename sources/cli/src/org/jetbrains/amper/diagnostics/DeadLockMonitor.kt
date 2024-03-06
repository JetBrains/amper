/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.diagnostics

import jetbrains.buildServer.messages.serviceMessages.PublishArtifacts
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.debug.DebugProbes
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.amper.cli.AmperBuildLogsRoot
import org.jetbrains.amper.cli.CliEnvironmentInitializer
import org.jetbrains.amper.core.AmperBuild
import org.jetbrains.amper.intellij.ThreadDumper
import org.jetbrains.amper.util.isUnderTeamCity
import java.io.PrintStream
import java.time.Duration
import java.time.Instant
import kotlin.io.path.createDirectories
import kotlin.io.path.pathString
import kotlin.time.toKotlinDuration

object DeadLockMonitor {
    // we probably need to think of better mechanism of deadlock detection disabled
    // since adding this call to any task that calls user code is unfeasible in the long run,
    // or we may change a way of detecting deadlocks in coroutines
    fun disable() {
        disabled = true
    }

    @OptIn(DelicateCoroutinesApi::class, ExperimentalCoroutinesApi::class)
    fun install(logsRoot: AmperBuildLogsRoot) {
        require(DebugProbes.isInstalled) {
            "DebugProbes must be installed to use DebugProbes.dumpCoroutines"
        }

        GlobalScope.launch {
            try {
                var timesToReport = MAX_NUMBER_OF_TIMES_TO_REPORT
                while (!disabled) {
                    delay(checkInterval.toKotlinDuration())

                    val lastLog = LastLogMonitoringWriter.lastLogEntryTimestamp
                    val lastLogDuration = Duration.between(lastLog, Instant.now())
                    if (lastLogDuration > considerDeadInterval) {
                        val dumpFile = logsRoot.path.resolve("thread-dump-${CliEnvironmentInitializer.currentTimestamp()}-${AmperBuild.BuildNumber}.txt")
                        System.err.println("Amper has not logged anything at DEBUG log level for $lastLogDuration, possible deadlock, dumping current state to $dumpFile")

                        dumpFile.parent.createDirectories()
                        PrintStream(dumpFile.toFile(), Charsets.UTF_8).use { printStream ->
                            printStream.println(ThreadDumper.dumpThreadsToString())
                            DebugProbes.dumpCoroutines(out = printStream)
                        }

                        if (isUnderTeamCity) {
                            // immediately publish it to TeamCity artifacts to get build failure reason faster
                            println(PublishArtifacts(dumpFile.pathString).asString())
                        }

                        timesToReport -= 1
                        if (timesToReport <= 0) {
                            break
                        }
                    }
                }
            } catch (t: Throwable) {
                t.printStackTrace()
            }
        }
    }

    @Volatile
    private var disabled = false

    private val considerDeadInterval = Duration.ofMinutes(1)
    private val checkInterval = considerDeadInterval.dividedBy(2)
    private const val MAX_NUMBER_OF_TIMES_TO_REPORT = 10
}
