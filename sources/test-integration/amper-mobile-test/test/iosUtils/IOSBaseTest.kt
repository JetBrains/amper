/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package iosUtils

import TestBase
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.MacOnly
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * A base class for testing iOS modules and projects, providing utilities to clean up test directories,
 * prepare project builds, and launch test processes.
 */
@MacOnly
open class IOSBaseTest : TestBase() {

    /**
     * Copies the project with the given [projectSource] to a temporary folder,
     * runs the iOS application built with [buildIosApp] on an iOS simulator.
     *
     * @param projectsDir in-source projects directory for [ProjectSource.Local] projects.
     */
    @OptIn(ProcessLeak::class)
    protected fun prepareExecution(
        projectSource: ProjectSource,
        projectsDir: Path,
        bundleIdentifier: String,
        buildIosApp: suspend (projectDir: Path) -> Path,
    ) = runBlocking {
        val copiedProjectDir = copyProjectToTempDir(projectSource, projectsDir)
        val appDir = buildIosApp(copiedProjectDir)
        SimulatorManager.launchSimulator()
        val appFile = appDir.findAppFile()
        println("Running iOS app ${appFile.name} from $appDir")
        AppManager.installAndVerifyAppLaunch(appFile = appFile, appBundleId = bundleIdentifier)
    }

    /**
     * Prepares and runs the iOS test for the Standalone-based project from [projectSource] using the given
     * [bundleIdentifier].
     *
     * If [iosAppModuleName] is specified, the corresponding module is used as the iOS app to test,
     * otherwise the root module is expected to be the iOS app.
     */
    internal fun testRunnerStandalone(projectSource: ProjectSource, bundleIdentifier: String, iosAppModuleName: String? = null) {
        val examplesProjectsDir = Dirs.amperCheckoutRoot.resolve("examples")
        prepareExecution(
            projectSource = projectSource,
            projectsDir = examplesProjectsDir,
            bundleIdentifier = bundleIdentifier,
        ) { projectDir ->
            buildIosAppWithStandaloneAmper(projectRootDir = projectDir, iosAppModuleName)
        }
    }

    /**
     * Builds the iOS app for the project located at [projectRootDir] using standalone Amper.
     *
     * If [iosAppModuleName] is specified, the corresponding module is used as the iOS app to test,
     * otherwise the root module is expected to be the iOS app.
     */
    private suspend fun buildIosAppWithStandaloneAmper(
        projectRootDir: Path,
        iosAppModuleName: String?,
    ): Path {
        val rootProjectName = projectRootDir.name
        val moduleName = iosAppModuleName ?: rootProjectName

        if (!projectRootDir.isDirectory()) {
            error("The path '$projectRootDir' does not exist or is not a directory.")
        }
        runAmper(
            workingDir = projectRootDir,
            args = listOf("build", "-m", moduleName, "-p", "iosSimulatorArm64"),
            // xcode will in turn call Amper with this env
            environment = baseEnvironmentForWrapper(),
            assertEmptyStdErr = false,
        )
        return projectRootDir / "build/tasks/_${moduleName}_buildIosAppIosSimulatorArm64Debug/bin/Debug-iphonesimulator"
    }

    private fun Path.findAppFile(): Path =
        listDirectoryEntries("*.app").firstOrNull() ?: error("app file not found in $this")
}
