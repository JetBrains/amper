/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.core.messages.*
import org.slf4j.LoggerFactory

internal class SLF4JProblemReporterContext : ProblemReporterContext {
    override val problemReporter: SLF4JProblemReporter = SLF4JProblemReporter(AmperAndroidIntegrationSettingsPlugin::class.java)
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
