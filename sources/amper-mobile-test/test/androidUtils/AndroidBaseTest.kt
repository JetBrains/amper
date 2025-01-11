/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package androidUtils

import TestBase
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.test.Dirs
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.time.Duration.Companion.minutes

/**
 * Main test class that provides methods to run Android tests.
 */
open class AndroidBaseTest : TestBase() {

    /** Path to the directory containing E2E test projects for Gradle-based tests */
    private val gradleE2eTestProjectsPath = Dirs.amperSourcesRoot / "gradle-e2e-test/testData/projects"

    /**
     * Sets up and executes a test environment for [projectName] located in [projectsDir],
     * including assembling the app, optionally using [applicationId] for custom APK setup.
     * The [buildApk] function is used to build the APK via the build tool used for the test.
     */
    private fun prepareExecution(
        projectName: String,
        projectsDir: Path,
        applicationId: String? = null,
        buildApk: suspend (projectDir: Path) -> Path,
    ) = runTest(timeout = 15.minutes) {
        val copiedProjectDir = copyProjectToTempDir(projectName, projectsDir)
        val targetApkPath = buildApk(copiedProjectDir)
        val testAppApkPath = InstrumentedTestApp.assemble(applicationId)
        ApkManager.installApk(testAppApkPath)
        ApkManager.installApk(targetApkPath)
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
        androidAppModuleName: String? = null,
    ) {
        val androidTestProjectsPath = Dirs.amperTestProjectsRoot / "android"

        prepareExecution(projectName, androidTestProjectsPath, applicationId) { projectDir ->
            buildApkWithAmper(projectDir, moduleName = androidAppModuleName ?: projectName)
        }
    }

    /**
     * Builds the Android debug APK for the given [moduleName] in the given [projectDir].
     *
     * @return the path to the built APK
     */
    private suspend fun buildApkWithAmper(projectDir: Path, moduleName: String): Path {
        runAmper(
            workingDir = projectDir,
            args = listOf("task", ":$moduleName:buildAndroidDebug"),
        )
        // internal Amper convention based on the task name
        return projectDir / "build/tasks/_${moduleName}_buildAndroidDebug/gradle-project-debug.apk"
    }

    /**
     * Runs Gradle-based tests for the Android project specified by [projectName] using Amper.
     *
     * If [androidAppSubprojectName] is specified, the corresponding subproject is used as the Android app to test,
     * otherwise the root project is expected to be the Android app.
     */
    internal fun testRunnerGradle(projectName: String, androidAppSubprojectName: String? = null) {
        prepareExecution(projectName, gradleE2eTestProjectsPath) { projectDir ->
            putAmperToGradleFile(projectDir, runWithPluginClasspath = true)
            buildApkWithGradle(projectDir, projectName, androidAppSubprojectName)
        }
    }

    /**
     * Builds the Android debug APK for the project in the given [projectRootDir].
     *
     * If [androidAppSubprojectName] is specified, the corresponding subproject is used as the Android app to test,
     * otherwise the root project is expected to be the Android app.
     *
     * @return the path to the built APK
     */
    private suspend fun buildApkWithGradle(
        projectRootDir: Path,
        rootProjectName: String,
        androidAppSubprojectName: String?
    ): Path {
        assembleTargetApp(projectRootDir)

        return if (androidAppSubprojectName != null) {
            projectRootDir / "$androidAppSubprojectName/build/outputs/apk/debug/$androidAppSubprojectName-debug.apk"
        } else {
            projectRootDir / "build/outputs/apk/debug/$rootProjectName-debug.apk"
        }
    }
}
