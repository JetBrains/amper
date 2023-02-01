/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.messages.renderMessage
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean

object CliProblemReporterContext : ProblemReporterContext {
    object CliProblemReporter : ProblemReporter {
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
            }
        }
    }

    override val problemReporter: CliProblemReporter = CliProblemReporter
}
