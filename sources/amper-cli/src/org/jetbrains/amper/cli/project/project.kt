/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.project

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.CliProblemReporter
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.AmperModuleFileSource
import org.jetbrains.amper.frontend.AmperModuleInvalidPathSource
import org.jetbrains.amper.frontend.AmperModuleProgrammaticSource
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.plugins.preparePlugins
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString

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

/**
 * Reads the [Model] from the Amper project files in this [org.jetbrains.amper.cli.CliContext].
 *
 * @throws UserReadableError if any error (or fatal error) is diagnosed in the model
 */
internal suspend fun CliContext.preparePluginsAndReadModel(): Model {
    spanBuilder("Prepare plugins").use {
        preparePlugins(context = this@preparePluginsAndReadModel)
    }

    val model = spanBuilder("Read model from Amper files").use {
        with(CliProblemReporter) {
            projectContext.readProjectModel()
        }
    }

    // In CLI, we don't only fail on fatal errors, but on all errors, because the model would be incorrect otherwise
    if (model == null || CliProblemReporter.wereProblemsReported()) {
        userReadableError("failed to read Amper model, refer to the errors above")
    }

    checkUniqueModuleNames(model.modules)
    return model
}

private fun checkUniqueModuleNames(modules: List<AmperModule>) {
    for ((moduleUserReadableName, moduleList) in modules.groupBy { it.userReadableName }) {
        if (moduleList.size > 1) {
            val joinToString = moduleList.joinToString("\n") {
                when (val source = it.source) {
                    is AmperModuleFileSource -> source.buildFile.pathString
                    is AmperModuleInvalidPathSource -> source.invalidPath.pathString
                    is AmperModuleProgrammaticSource -> "(unknown)"
                }
            }
            userReadableError("Module name '${moduleUserReadableName}' is not unique, it's declared in:\n$joinToString")
        }
    }
}
