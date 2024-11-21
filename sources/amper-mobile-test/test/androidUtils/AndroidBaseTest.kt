/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package androidUtils

import TestBase
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.runTestInfinitely
import java.nio.file.Path
import kotlin.io.path.div

/**
 * Main test class that provides methods to run Android tests.
 */
open class AndroidBaseTest : TestBase() {

    /** Path to the directory containing E2E test projects for Gradle-based tests */
    private val gradleE2eTestProjectsPath = TestUtil.amperSourcesRoot / "gradle-e2e-test/testData/projects"

    /**
     * Sets up and executes a test environment for [projectName] located in [projectsDir],
     * including assembling the app, optionally using [applicationId] for custom APK setup.
     * The [buildApk] function is used to build the APK via the build tool used for the test.
     */
    private fun prepareExecution(
        projectName: String,
        projectsDir: Path,
        applicationId: String? = null,
        buildApk: suspend (Path) -> Unit,
    ) = runTestInfinitely {
        val copiedProjectDir = copyProjectToTempDir(projectName, projectsDir)
        buildApk(copiedProjectDir)
        ProjectPreparer.assembleTestApp(applicationId)
        ApkManager.installAndroidTestAPK()
        ApkManager.installTargetAPK(projectRootDir = copiedProjectDir, rootProjectName = projectName)
        ApkManager.runTestsViaAdb(applicationId)
    }

    /**
     * Executes standalone tests for an Android project specified by [projectName],
     * optionally using [applicationId] for custom APK setups,
     * and indicating if the project is multiplatform with [multiplatform].
     */
    internal fun testRunnerStandalone(
        projectName: String,
        applicationId: String? = null,
        multiplatform: Boolean = false
    ) {
        val androidTestProjectsPath = TestUtil.amperSourcesRoot.resolve("amper-backend-test/testData/projects/android")

        prepareExecution(projectName, androidTestProjectsPath, applicationId) { projectDir ->
            val taskPath = if (multiplatform) ":android-app:buildAndroidDebug" else ":$projectName:buildAndroidDebug"
            runAmper(
                workingDir = projectDir,
                args = listOf("task", taskPath),
            )
        }
    }

    /**
     * Runs Gradle-based tests for the Android project specified by [projectName] using Amper.
     */
    internal fun testRunnerGradle(projectName: String) {
        prepareExecution(projectName, gradleE2eTestProjectsPath) { projectDir ->
            putAmperToGradleFile(projectDir, runWithPluginClasspath = true)
            assembleTargetApp(projectDir)
        }
    }
}
