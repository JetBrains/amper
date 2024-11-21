/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package iosUtils

import TestBase
import iosUtils.GradleAssembleHelper.buildiOSAppGradle
import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.processes.ProcessLeak
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory

/**
 * A base class for testing iOS modules and projects, providing utilities to clean up test directories,
 * prepare project builds, and launch test processes.
 */
open class IOSBaseTest : TestBase() {

    /**
     * Copies the specified [projectName] to temp folder, runs a given [projectAction] (depends on a type of Amper) on it,
     * and launches the iOS simulator. Configures the setup based on [multiplatform]
     * and [standalone] flags.
     */
    @OptIn(ProcessLeak::class)
    protected fun prepareExecution(
        projectName: String,
        projectPath: Path,
        bundleIdentifier: String,
        multiplatform: Boolean = false,
        standalone: Boolean,
        buildIosApp: suspend (String) -> Unit
    ) = runBlocking {
        copyProject(projectName, projectPath)
        buildIosApp(projectName)
        SimulatorManager.launchSimulator()
        AppManager.launchTest(
            projectRootDir = tempProjectsDir / projectName,
            rootProjectName = projectName,
            bundleIdentifier = bundleIdentifier,
            multiplatform = multiplatform,
            standalone = standalone,
        )
    }

    /**
     * Prepares and runs the iOS test for a Gradle-based project
     */
    internal fun testRunnerGradle(projectName: String, bundleIdentifier: String, multiplatform: Boolean = false) {
        val examplesGradleProjectsDir = TestUtil.amperCheckoutRoot.resolve("examples-gradle")
        prepareExecution(projectName, examplesGradleProjectsDir, bundleIdentifier, multiplatform, false) {
            buildIosAppWithGradle(it, multiplatform)
        }
    }

    /**
     * Prepares and runs the iOS test for a Standalone-based project
     */
    internal fun testRunnerStandalone(projectName: String, bundleIdentifier: String, multiplatform: Boolean = false) {
        val examplesStandaloneProjectsDir = TestUtil.amperCheckoutRoot.resolve("examples-standalone")
        prepareExecution(projectName, examplesStandaloneProjectsDir, bundleIdentifier, multiplatform, true) {
            buildIosAppWithStandaloneAmper(it, multiplatform)
        }
    }

    /**
     * Builds the iOS app for the project named [projectName] using Gradle.
     */
    private suspend fun buildIosAppWithGradle(projectName: String, multiplatform: Boolean) {
        val runWithPluginClasspath = true
        val projectDirectory = tempProjectsDir / projectName

        if (projectDirectory.exists() && projectDirectory.isDirectory()) {
            buildiOSAppGradle(projectDirectory, runWithPluginClasspath, multiplatform)
        } else {
            println("Project directory '${projectDirectory.absolutePathString()}' does not exist or is not a directory.")
        }
    }

    /**
     * Builds the iOS app for the project named [projectName] using standalone Amper.
     */
    private suspend fun buildIosAppWithStandaloneAmper(projectName: String, multiplatform: Boolean = false) {
        val projectDir = tempProjectsDir / projectName
        val taskPath = if (multiplatform) ":ios-app:buildIosAppIosSimulatorArm64" else ":$projectName:buildIosAppIosSimulatorArm64"

        if (projectDir.exists() && projectDir.isDirectory()) {
            runAmper(
                workingDir = projectDir,
                args = listOf("task", taskPath),
                // xcode will in turn call Amper with this env
                environment = baseEnvironmentForWrapper(),
                assertEmptyStdErr = false,
            )
        } else {
            println("The path '$projectDir' does not exist or is not a directory.")
        }
    }
}
