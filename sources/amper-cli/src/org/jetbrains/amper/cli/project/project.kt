/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.project

import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute

/**
 * Creates the [AmperProjectContext] of the current project based on the given [explicitProjectRoot], of finds one
 * by starting at the current directory
 */
internal suspend fun findProjectContext(explicitProjectRoot: Path?, explicitBuildRoot: Path?): AmperProjectContext? =
    spanBuilder("Find Amper project context").use {
        with(CliProblemReporter) {
            val context = if (explicitProjectRoot != null) {
                StandaloneAmperProjectContext.create(explicitProjectRoot.absolute(), explicitBuildRoot?.absolute())
                    ?: userReadableError(
                        "The given path '$explicitProjectRoot' is not a valid Amper project root directory. " +
                                "Make sure you have a project file or a module file at the root of your Amper project."
                    )
            } else {
                StandaloneAmperProjectContext.find(
                    start = Path(System.getProperty("user.dir")),
                    buildDir = explicitBuildRoot?.absolute(),
                )
            }
            if (wereProblemsReported()) {
                userReadableError("Aborting because there were errors in the Amper project file, please see above.")
            }
            context
        }
    }
