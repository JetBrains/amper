/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.messages.renderMessage
import org.slf4j.LoggerFactory

internal class SLF4JProblemReporterContext : ProblemReporterContext {
    override val problemReporter: SLF4JProblemReporter = SLF4JProblemReporter(BindingSettingsPlugin::class.java)
}

internal class SLF4JProblemReporter(loggerClass: Class<*> = ProblemReporter::class.java) : CollectingProblemReporter() {
    companion object {
        private const val ERROR_PREFIX = "  - "
        private const val ERROR_INDENT = "    "
    }

    private val logger = LoggerFactory.getLogger(loggerClass)

    override fun doReportMessage(message: BuildProblem) {
        when (message.level) {
            Level.Warning -> logger.warn(renderMessage(message))
            Level.Error -> logger.error(renderMessage(message))
            Level.Fatal -> logger.error(renderMessage(message))
        }
    }

    fun getErrors(): List<String> = problems.map(::renderMessage)

    fun getGradleError(): String =
        """
        |Amper model initialization failed. 
        |Errors: 
        |$ERROR_PREFIX${getErrors().joinToString("\n|$ERROR_PREFIX") { it.replace("\n", "\n$ERROR_INDENT") }}
        |See logs for details.""".trimMargin()
}
