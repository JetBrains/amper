/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.amper.android.AndroidSdkDetector
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.spanBuilder
import org.jetbrains.amper.core.use
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.tasks.CommonRunSettings
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.isDirectory

class CliContext private constructor(
    val projectContext: AmperProjectContext,
    val userCacheRoot: AmperUserCacheRoot,
    val projectTempRoot: AmperProjectTempRoot,
    // in the future it'll be customizable to support out-of-tree builds, e.g., on CI
    val buildOutputRoot: AmperBuildOutputRoot,
    val buildLogsRoot: AmperBuildLogsRoot,
    val commonRunSettings: CommonRunSettings,
    val taskExecutionMode: TaskExecutor.Mode,
    val mavenLocalRepository: MavenLocalRepository,
    val terminal: Terminal,
    val androidHomeRoot: AndroidHomeRoot,
    /**
     * Background scope is terminated when project-related activities are finished (e.g., on Amper exit)
     */
    val backgroundScope: CoroutineScope,
) {
    val projectRoot: AmperProjectRoot = AmperProjectRoot(projectContext.projectRootDir.toNioPath())

    companion object {
        fun create(
            explicitProjectRoot: Path?,
            taskExecutionMode: TaskExecutor.Mode = TaskExecutor.Mode.FAIL_FAST,
            commonRunSettings: CommonRunSettings = CommonRunSettings(),
            buildOutputRoot: AmperBuildOutputRoot? = null,
            userCacheRoot: AmperUserCacheRoot,
            currentTopLevelCommand: String,
            backgroundScope: CoroutineScope,
            terminal: Terminal,
            androidHomeRoot: AndroidHomeRoot? = null,
        ): CliContext {
            require(currentTopLevelCommand.isNotBlank()) {
                "currentTopLevelCommand should not be blank"
            }

            val amperProjectContext = spanBuilder("CLI Setup: create project context").use {
                with(CliProblemReporterContext) {
                    createProjectContext(explicitProjectRoot).also {
                        if (problemReporter.wereProblemsReported()) {
                            userReadableError("aborting because there were errors in the Amper project file, please see above")
                        }
                    }
                }
            }

            val rootPath = amperProjectContext.projectRootDir.toNioPath()

            val buildOutputRootNotNull = buildOutputRoot ?: run {
                val defaultBuildDir = rootPath.resolve("build").also { it.createDirectories() }
                AmperBuildOutputRoot(defaultBuildDir)
            }

            val tempDir = buildOutputRootNotNull.path.resolve("temp").also { it.createDirectories() }

            val currentTimestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            val logsDir = buildOutputRootNotNull.path
                .resolve("logs")
                .resolve("amper_${currentTimestamp}_$currentTopLevelCommand")
                .also { it.createDirectories() }

            val androidHomeRootNotNull = androidHomeRoot ?: AndroidHomeRoot(
                (AndroidSdkDetector.detectSdkPath() ?: error("Android SDK detector not found"))
                    .also { it.createDirectories() }
            )

            return CliContext(
                projectContext = amperProjectContext,
                buildOutputRoot = buildOutputRootNotNull,
                projectTempRoot = AmperProjectTempRoot(tempDir),
                buildLogsRoot = AmperBuildLogsRoot(logsDir),
                userCacheRoot = userCacheRoot,
                commonRunSettings = commonRunSettings,
                taskExecutionMode = taskExecutionMode,
                mavenLocalRepository = MavenLocalRepository(),
                terminal = terminal,
                backgroundScope = backgroundScope,
                androidHomeRoot = androidHomeRootNotNull
            )
        }
    }
}

context(ProblemReporterContext)
private fun createProjectContext(explicitProjectRoot: Path?): StandaloneAmperProjectContext =
    if (explicitProjectRoot != null) {
        StandaloneAmperProjectContext.create(explicitProjectRoot)
            ?: userReadableError(
                "The given path '$explicitProjectRoot' is not a valid Amper project root directory. " +
                        "Make sure you have a project file or a module file at the root of your Amper project."
            )
    } else {
        StandaloneAmperProjectContext.find(start = Path(System.getProperty("user.dir")))
            ?: userReadableError(
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
