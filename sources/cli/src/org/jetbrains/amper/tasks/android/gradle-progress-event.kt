/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.android

import org.gradle.tooling.events.FailureResult
import org.gradle.tooling.events.FinishEvent
import org.gradle.tooling.events.ProgressEvent
import org.gradle.tooling.events.StartEvent
import org.gradle.tooling.events.SuccessResult
import org.slf4j.LoggerFactory
import java.nio.file.Path


val logger = LoggerFactory.getLogger(object {}.javaClass)

internal fun ProgressEvent.handle(stdoutPath: Path, stderrPath: Path) {
    if (descriptor.name == "Run build") {
        when (this) {
            is StartEvent -> logger.info("Gradle build started")
            is FinishEvent -> when (result) {
                is SuccessResult -> logger.info("Gradle build finished successfully")
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
