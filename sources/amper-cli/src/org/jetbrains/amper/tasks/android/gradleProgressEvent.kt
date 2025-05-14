/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.SuccessResult
import org.gradle.tooling.events.problems.ProblemAggregationEvent
import org.gradle.tooling.events.problems.ProblemEvent
import org.gradle.tooling.events.problems.Severity.ADVICE
import org.gradle.tooling.events.problems.Severity.ERROR
import org.gradle.tooling.events.problems.Severity.WARNING
import org.gradle.tooling.events.problems.SingleProblemEvent
import org.slf4j.LoggerFactory
import java.nio.file.Path


val logger = LoggerFactory.getLogger(object {}.javaClass)

@Suppress("UnstableApiUsage")
internal fun ProgressEvent.handle(stdoutPath: Path, stderrPath: Path) {
    if (this is ProblemEvent) {
        val (definition, detailsList) = when(this) {
            is SingleProblemEvent -> definition to listOf(details)
            is ProblemAggregationEvent ->
                problemAggregation.definition to problemAggregation.problemContext.map { it.details }
            else -> null to emptyList()
        }
        val loggingFunc: (String) -> Unit = when(definition?.severity) {
            ADVICE -> logger::info
            WARNING -> logger::warn
            ERROR -> logger::error
            else -> logger::info
        }
        // TODO: BasePlugin.archiveBaseName will be deprecated in 9.0; however, agp now is using this field
        // eventually, after agp upgrade it should be deleted
        if (definition?.id?.name != "the-basepluginextension-archivesbasename-property-has-been-deprecated") {
            detailsList.forEach { details ->
                loggingFunc(details.details)
            }
        }
    }
    if (descriptor.name == "Run build") {
        when (this) {
            is StartEvent -> logger.info("Gradle build started")
            is FinishEvent -> when (result) {
                is SuccessResult -> {
                    logger.info("Gradle build finished successfully")
                }
                is FailureResult -> {
                    logger.error("Gradle build failed with errors:")
                    for (error in (result as FailureResult).failures) {
                        val causes = ArrayDeque(error.causes)
                        while (causes.isNotEmpty()) {
                            val cause = causes.removeFirst()
                            logger.error(cause.message)
                            causes.addAll(cause.causes)
                        }
                    }

                    logger.error("See more details in the log files:")
                    logger.error("  stdout: $stdoutPath")
                    logger.error("  stderr: $stderrPath")
                }
            }
        }
    }
}
