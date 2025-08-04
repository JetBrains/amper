/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.terminal.Terminal
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.format
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.util.DateTimeFormatForFilenames
import org.jetbrains.amper.util.nowInDefaultTimezone
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory

class CliContext(
    val commandName: String,
    val projectContext: AmperProjectContext,
    val userCacheRoot: AmperUserCacheRoot,
    val terminal: Terminal,
    val androidHomeRoot: AndroidHomeRoot,
) {
    val projectRoot: AmperProjectRoot = AmperProjectRoot(projectContext.projectRootDir.toNioPath())

    val buildOutputRoot: AmperBuildOutputRoot by lazy {
        AmperBuildOutputRoot(projectContext.projectBuildDir.createDirectories())
    }

    val projectTempRoot: AmperProjectTempRoot by lazy {
        AmperProjectTempRoot((projectContext.projectBuildDir / "temp").createDirectories())
    }

    /**
     * The root directory containing all logs for all Amper executions in the current project.
     */
    val projectLogsRoot: AmperProjectLogsRoot by lazy {
        AmperProjectLogsRoot((projectContext.projectBuildDir / "logs").createDirectories())
    }

    /**
     * The logs directory for the current Amper execution.
     */
    val currentLogsRoot: AmperBuildLogsRoot by lazy {
        val currentTimestamp = LocalDateTime.nowInDefaultTimezone().format(DateTimeFormatForFilenames)
        val pid = ProcessHandle.current().pid() // avoid clashes with concurrent Amper processes
        val currentLogsPath = projectLogsRoot.path.resolve("amper_${currentTimestamp}_${pid}_$commandName")
        AmperBuildLogsRoot(currentLogsPath.createDirectories())
    }

    companion object {
        /**
         * An absolute path to the wrapper script that the process currently runs under.
         */
        val wrapperScriptPath: Path by lazy {
            System.getProperty("amper.wrapper.path")?.takeIf { it.isNotBlank() }?.let(::Path)
                ?: error("Missing `amper.wrapper.path` property. Is your Amper distribution intact?")
        }
    }
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
