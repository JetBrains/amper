/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.problems.reporting.renderMessage
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

internal object CliProblemReporter : ProblemReporter {
    private val logger = LoggerFactory.getLogger("build")
    private val problemsWereReported = AtomicBoolean(false)

    fun wereProblemsReported() = problemsWereReported.get()

    override fun reportMessage(message: BuildProblem) {
        when (message.level) {
            Level.Warning -> logger.warn(renderMessage(message))
            Level.Error -> {
                logger.error(renderMessage(message))
                problemsWereReported.set(true)
            }
            Level.WeakWarning -> {
                logger.info(renderMessage(message))
            }
        }
    }
}
