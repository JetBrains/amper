/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import io.opentelemetry.api.trace.Span
import org.jetbrains.amper.cli.logging.DoNotLogToTerminalCookie
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.Level
import org.jetbrains.amper.problems.reporting.renderMessage
import org.slf4j.LoggerFactory

/**
 * [MavenResolver] inheritor that logs encountered problems to the CLI.
 */
class CliReportingMavenResolver(
    userCacheRoot: AmperUserCacheRoot,
    incrementalCache: IncrementalCache,
) : MavenResolver(userCacheRoot, incrementalCache) {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun processProblems(buildProblems: List<BuildProblem>, span: Span, resolveSourceMoniker: String) {
        for (buildProblem in buildProblems) {
            when (buildProblem.level) {
                Level.WeakWarning -> logger.info(buildProblem.message)
                Level.Warning -> logger.warn(buildProblem.message)
                Level.Error -> {
                    span.recordException(MavenResolverException(buildProblem.message))
                    DoNotLogToTerminalCookie.use {
                        logger.error(buildProblem.message)
                    }
                }
            }
        }
        
        val errors = buildProblems.filter { it.level.atLeastAsSevereAs(Level.Error) }
        if (errors.isNotEmpty()) {
            userReadableError(
                "Unable to resolve dependencies for $resolveSourceMoniker:\n\n" +
                        errors.joinToString("\n\n") { renderMessage(it) })
        }
    }
}