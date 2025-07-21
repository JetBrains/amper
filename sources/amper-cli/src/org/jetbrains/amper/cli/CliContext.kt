/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.terminal.Terminal
import org.jetbrains.amper.android.AndroidSdkDetector
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.telemetry.spanBuilder
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.tasks.AllRunSettings
import org.jetbrains.amper.telemetry.use
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

class CliContext private constructor(
    val commandName: String,
    val projectContext: AmperProjectContext,
    val userCacheRoot: AmperUserCacheRoot,
    val projectTempRoot: AmperProjectTempRoot,
    // in the future it'll be customizable to support out-of-tree builds, e.g., on CI
    val buildOutputRoot: AmperBuildOutputRoot,
    val runSettings: AllRunSettings,
    val terminal: Terminal,
    val androidHomeRoot: AndroidHomeRoot,
) {
    val projectRoot: AmperProjectRoot = AmperProjectRoot(projectContext.projectRootDir.toNioPath())

    /**
     * The root directory containing all logs for all Amper executions in the current project.
     */
    val projectLogsRoot: AmperProjectLogsRoot by lazy {
        AmperProjectLogsRoot(buildOutputRoot.path.resolve("logs").createDirectories())
    }

    /**
     * The logs directory for the current Amper execution.
     */
    val currentLogsRoot: AmperBuildLogsRoot by lazy {
        val currentTimestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
        val pid = ProcessHandle.current().pid() // avoid clashes with concurrent Amper processes
        val currentLogsPath = projectLogsRoot.path.resolve("amper_${currentTimestamp}_${pid}_$commandName")
        AmperBuildLogsRoot(currentLogsPath.createDirectories())
    }

    companion object {
        suspend fun create(
            explicitProjectRoot: Path?,
            runSettings: AllRunSettings = AllRunSettings(),
            explicitBuildOutputRoot: Path?,
            userCacheRoot: AmperUserCacheRoot,
            commandName: String,
            terminal: Terminal,
            androidHomeRoot: AndroidHomeRoot? = null,
        ): CliContext {
            require(commandName.isNotBlank()) { "commandName should not be blank" }

            val amperProjectContext = spanBuilder("Create Amper project context").use {
                with(CliProblemReporter) {
                    createProjectContext(
                        explicitProjectRoot = explicitProjectRoot?.toAbsolutePath(),
                        explicitBuildRoot = explicitBuildOutputRoot,
                    ).also {
                        if (wereProblemsReported()) {
                            userReadableError("aborting because there were errors in the Amper project file, please see above")
                        }
                    }
                }
            }

            val tempDir = amperProjectContext.projectBuildDir.resolve("temp")

            return CliContext(
                commandName = commandName,
                projectContext = amperProjectContext,
                buildOutputRoot = AmperBuildOutputRoot(amperProjectContext.projectBuildDir.createDirectories()),
                projectTempRoot = AmperProjectTempRoot(tempDir.createDirectories()),
                userCacheRoot = userCacheRoot,
                runSettings = runSettings,
                terminal = terminal,
                androidHomeRoot = androidHomeRoot ?: AndroidHomeRoot(
                    AndroidSdkDetector.detectSdkPath().createDirectories()
                ),
            )
        }

        /**
         * An absolute path to the wrapper script that the process currently runs under.
         */
        val wrapperScriptPath: Path by lazy {
            System.getProperty("amper.wrapper.path")?.takeIf { it.isNotBlank() }?.let(::Path)
                ?: error("Missing `amper.wrapper.path` property. Is your Amper distribution intact?")
        }
    }
}

context(_: ProblemReporter)
private fun createProjectContext(
    explicitProjectRoot: Path?,
    explicitBuildRoot: Path?,
): StandaloneAmperProjectContext =
    if (explicitProjectRoot != null) {
        StandaloneAmperProjectContext.create(explicitProjectRoot, explicitBuildRoot)
            ?: userReadableError(
                "The given path '$explicitProjectRoot' is not a valid Amper project root directory. " +
                        "Make sure you have a project file or a module file at the root of your Amper project."
            )
    } else {
        StandaloneAmperProjectContext.find(
            start = Path(System.getProperty("user.dir")),
            buildDir = explicitBuildRoot,
        ) ?: userReadableError(
            "No Amper project found in the current directory or above. " +
                    "Make sure you have a project file or a module file at the root of your Amper project, " +
                    "or specify --root explicitly to run tasks for a project located elsewhere."
        )
    }

data class AmperBuildOutputRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Build output root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Build output root is not an absolute path: $path"
        }
    }
}

data class AmperProjectLogsRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Logs root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Logs root is not an absolute path: $path"
        }
    }
}

data class AmperBuildLogsRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Build logs root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Build logs root is not an absolute path: $path"
        }
    }
}

data class AmperProjectTempRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Temp root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Temp root is not an absolute path: $path"
        }
    }
}

data class AmperProjectRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Project root is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Project root is not an absolute path: $path"
        }
    }
}

data class AndroidHomeRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Android home is not a directory: $path"
        }
        require(path.isAbsolute) {
            "Android home is not an absolute path: $path"
        }
    }
}
