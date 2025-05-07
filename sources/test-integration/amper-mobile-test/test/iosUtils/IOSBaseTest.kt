/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package iosUtils

import TestBase
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.MacOnly
import java.nio.file.Path
import kotlin.io.path.absolutePathString
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
     * Prepares and runs the iOS test for the Gradle-based project from [projectSource] using the given
     * [bundleIdentifier].
     *
     * If [iosAppSubprojectName] is specified, the corresponding subproject is used as the iOS app to test,
     * otherwise the root project is expected to be the iOS app.
     */
    internal fun testRunnerGradle(projectSource: ProjectSource, bundleIdentifier: String, iosAppSubprojectName: String? = null) {
        val examplesGradleProjectsDir = Dirs.amperCheckoutRoot.resolve("examples-gradle")
        prepareExecution(
            projectSource = projectSource,
            projectsDir = examplesGradleProjectsDir,
            bundleIdentifier = bundleIdentifier,
        ) { projectDir ->
            buildIosAppWithGradle(projectRootDir = projectDir, iosAppSubprojectName)
        }
    }

    /**
     * Prepares and runs the iOS test for the Standalone-based project from [projectSource] using the given
     * [bundleIdentifier].
     *
     * If [iosAppModuleName] is specified, the corresponding module is used as the iOS app to test,
     * otherwise the root module is expected to be the iOS app.
     */
    internal fun testRunnerStandalone(projectSource: ProjectSource, bundleIdentifier: String, iosAppModuleName: String? = null) {
        val examplesStandaloneProjectsDir = Dirs.amperCheckoutRoot.resolve("examples-standalone")
        prepareExecution(
            projectSource = projectSource,
            projectsDir = examplesStandaloneProjectsDir,
            bundleIdentifier = bundleIdentifier,
        ) { projectDir ->
            buildIosAppWithStandaloneAmper(projectRootDir = projectDir, iosAppModuleName)
        }
    }

    /**
     * Builds the iOS app for the project located at [projectRootDir] using Gradle.
     *
     * If [iosAppSubprojectName] is specified, the corresponding subproject is used as the iOS app to test,
     * otherwise the root project is expected to be the iOS app.
     */
    private suspend fun buildIosAppWithGradle(
        projectRootDir: Path,
        iosAppSubprojectName: String?,
    ): Path {
        val rootProjectName = projectRootDir.name
        if (!projectRootDir.isDirectory()) {
            error("Project directory '${projectRootDir.absolutePathString()}' does not exist or is not a directory.")
        }
        putAmperToGradleFile(projectRootDir, runWithPluginClasspath = true)
        assembleTargetApp(projectRootDir, iosAppSubprojectName)

        return if (iosAppSubprojectName != null) {
            projectRootDir / "$iosAppSubprojectName/build/bin/$iosAppSubprojectName/Debug-iphonesimulator"
        } else {
            projectRootDir / "build/bin/$rootProjectName/Debug-iphonesimulator"
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
        val taskPath = ":$moduleName:buildIosAppIosSimulatorArm64" // TODO should we use 'build -m $module' instead?

        if (!projectRootDir.isDirectory()) {
            error("The path '$projectRootDir' does not exist or is not a directory.")
        }
        runAmper(
            workingDir = projectRootDir,
            args = listOf("task", taskPath),
            // xcode will in turn call Amper with this env
            environment = baseEnvironmentForWrapper(),
            assertEmptyStdErr = false,
        )
        return projectRootDir / "build/tasks/_${moduleName}_buildIosAppIosSimulatorArm64/bin/Debug-iphonesimulator"
    }

    private fun Path.findAppFile(): Path =
        listDirectoryEntries("*.app").firstOrNull() ?: error("app file not found in $this")
}
