/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import com.github.ajalt.mordant.terminal.Terminal
import com.sun.jna.platform.win32.KnownFolders
import com.sun.jna.platform.win32.Shell32Util
import org.jetbrains.amper.dependency.resolution.MavenLocalRepository
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.tasks.CommonRunSettings
import org.jetbrains.amper.util.OS
import org.jetbrains.amper.util.OS.Type.Linux
import org.jetbrains.amper.util.OS.Type.Mac
import org.jetbrains.amper.util.OS.Type.Windows
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.isDirectory

class ProjectContext(
    val projectRoot: AmperProjectRoot,
    val userCacheRoot: AmperUserCacheRoot,
    val projectTempRoot: AmperProjectTempRoot,
    // in the future it'll be customizable to support out-of-tree builds, e.g., on CI
    val buildOutputRoot: AmperBuildOutputRoot,
    val buildLogsRoot: AmperBuildLogsRoot,
    val commonRunSettings: CommonRunSettings,
    val taskExecutionMode: TaskExecutor.Mode,
    val mavenLocalRepository: MavenLocalRepository,
    val terminal: Terminal,
) {
    companion object {
        fun create(
            projectRoot: Path,
            taskExecutionMode: TaskExecutor.Mode = TaskExecutor.Mode.FAIL_FAST,
            commonRunSettings: CommonRunSettings = CommonRunSettings(),
            buildOutputRoot: AmperBuildOutputRoot? = null,
            userCacheRoot: AmperUserCacheRoot? = null,
            currentTopLevelCommand: String,
        ): ProjectContext {
            require(currentTopLevelCommand.isNotBlank()) {
                "currentTopLevelCommand should not be blank"
            }

            val buildOutputRootNotNull = buildOutputRoot ?: run {
                val defaultBuildDir = projectRoot.resolve("build").also { it.createDirectories() }
                AmperBuildOutputRoot(defaultBuildDir)
            }

            val userCacheRootNotNull = userCacheRoot ?: AmperUserCacheRoot.fromCurrentUser()

            for (path in listOf(userCacheRootNotNull.path, buildOutputRootNotNull.path, projectRoot)) {
                require(path.isAbsolute) {
                    "Path must be absolute: $path"
                }
            }

            val tempDir = buildOutputRootNotNull.path.resolve("temp").also { it.createDirectories() }

            val currentTimestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            val logsDir = buildOutputRootNotNull.path
                .resolve("logs")
                .resolve("amper_${currentTimestamp}_$currentTopLevelCommand")
                .also { it.createDirectories() }

            return ProjectContext(
                projectRoot = AmperProjectRoot(projectRoot),
                buildOutputRoot = buildOutputRootNotNull,
                projectTempRoot = AmperProjectTempRoot(tempDir),
                buildLogsRoot = AmperBuildLogsRoot(logsDir),
                userCacheRoot = userCacheRootNotNull,
                commonRunSettings = commonRunSettings,
                taskExecutionMode = taskExecutionMode,
                mavenLocalRepository = MavenLocalRepository(),
                terminal = Terminal(),
            )
        }
    }
}

data class AmperUserCacheRoot(val path: Path) {
    companion object {
        fun fromCurrentUser(): AmperUserCacheRoot {
            val userHome = Path.of(System.getProperty("user.home"))

            val localAppData = when (OS.type) {
                Windows -> Shell32Util.getKnownFolderPath(KnownFolders.FOLDERID_LocalAppData)?.let { Path.of(it) } ?: (userHome / "AppData/Local")
                Mac -> userHome / "Library/Caches"
                Linux -> {
                    val xdgCacheHome = System.getenv("XDG_CACHE_HOME")
                    if (xdgCacheHome.isNullOrBlank()) userHome.resolve(".cache") else Path.of(xdgCacheHome)
                }
            }

            val localAppDataAmper = localAppData.resolve("Amper")

            return AmperUserCacheRoot(localAppDataAmper)
        }
    }
}

data class AmperBuildOutputRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Build output root is not a directory: $path"
        }
    }
}

data class AmperBuildLogsRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Build logs root is not a directory: $path"
        }
    }
}

data class AmperProjectTempRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Temp root is not a directory: $path"
        }
    }
}

data class AmperProjectRoot(val path: Path) {
    init {
        require(path.isDirectory()) {
            "Project root is not a directory: $path"
        }
    }
}
