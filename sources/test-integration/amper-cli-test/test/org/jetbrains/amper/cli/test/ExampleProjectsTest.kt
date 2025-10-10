/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.cli.test.utils.getTaskOutputPath
import org.jetbrains.amper.cli.test.utils.readTelemetrySpans
import org.jetbrains.amper.cli.test.utils.runSlowTest
import org.jetbrains.amper.cli.test.utils.withTelemetrySpans
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.assertEqualsWithDiff
import org.jetbrains.amper.test.spans.assertJavaCompilationSpan
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.kotlinJvmCompilationSpans
import org.jetbrains.amper.test.spans.kotlinNativeCompilationSpans
import org.jetbrains.amper.test.spans.withAmperModule
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ExampleProjectsTest: AmperCliTestBase() {

    private fun exampleProject(name: String): Path = Dirs.examplesRoot.resolve(name)

    companion object {

        private val knownWarnings = listOf(
            // TODO fix Android Gradle builds to avoid this warning - we should use Gradle 9 anyway
            // https://youtrack.jetbrains.com/issue/AMPER-4751/Gradle-warning-in-Android-based-example-projects
            // https://youtrack.jetbrains.com/issue/AMPER-4752/Upgrade-to-Gradle-9-in-Android-delegated-builds
            "Starting with Gradle 9.0, this property will be ignored by Gradle",
            // From iOS builds - shouldn't really be a warning at all
            "The Info.plist already exists, no need to generate the default one.",
            // Probably shouldn't warn in debug mode - is this an Amper bug or a config issue?
            // https://youtrack.jetbrains.com/issue/AMPER-4753/iOS-warning-in-compose-multiplatform-example-project
            "`DEVELOPMENT_TEAM` build setting is not detected in the Xcode project. Adding `CODE_SIGNING_ALLOWED=NO` to disable signing. You can still sign the app manually later.",
            // Maybe this can be fixed with iOS project configuration
            // https://youtrack.jetbrains.com/issue/AMPER-4757/iOS-warning-in-compose-multiplatform-example-project-All-interface-orientations-must-be-supported
            "All interface orientations must be supported unless the app requires full screen",
        )

        // Linking and xcodebuild produce some warnings on stderr (said warnings are ignored specifically)
        private val knownProjectsWithNonEmptyStderr = setOf(
            "compose-desktop",
            "compose-ios",
        )

        // We use directory names, not paths, so the preview of the tests in IDE and TC is more readable
        @JvmStatic
        private fun findExampleProjects(): List<String> =
            Dirs.examplesRoot.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }
    }

    @ParameterizedTest
    @MethodSource("findExampleProjects")
    fun `example project can be built without errors or warnings`(projectName: String) = runSlowTest {
        val buildResult = runCli(
            projectRoot = exampleProject(projectName),
            "build",
            assertEmptyStdErr = projectName !in knownProjectsWithNonEmptyStderr,
            configureAndroidHome = true, // no need to be granular by project here, we'll install them once
        )
        val warnings = buildResult.stdoutClean.lines().filter { "WARN" in it }
        val unexpectedWarnings = warnings.filterNot { knownWarnings.any { warning -> it.contains(warning) } }
        assertEqualsWithDiff(unexpectedWarnings, emptyList(), "Unexpected warnings in $projectName")
    }

    @Test
    @MacOnly
    fun `compose-multiplatform`() = runSlowTest {
        val tasksResult = runCli(
            projectRoot = exampleProject("compose-multiplatform"),
            "show", "tasks",
            configureAndroidHome = true,
            copyToTempDir = true,
        )
        with(tasksResult) {
            (jvmBaseTasks + jvmTestTasks + iosLibraryTasks + androidTestTasks).forEach {
                assertContains(stdout, ":shared:$it")
            }
            androidAppTasks.forEach { assertContains(stdout, ":android-app:$it") }
            jvmAppTasks.forEach { assertContains(stdout, ":jvm-app:$it") }
            iosAppTasks.forEach { assertContains(stdout, ":ios-app:$it") }
        }

        val buildResult = runCli(
            projectRoot = tasksResult.projectRoot,
            "build",
            configureAndroidHome = true,
            assertEmptyStdErr = false,
        )
        buildResult.withTelemetrySpans {
            // main/test for Jvm + main/test * debug for Android.
            kotlinJvmCompilationSpans.withAmperModule("shared").assertTimes(4)

            // debug for Android (no test sources).
            kotlinJvmCompilationSpans.withAmperModule("android-app").assertTimes(1)

            // main for Jvm (no test sources).
            kotlinJvmCompilationSpans.withAmperModule("jvm-app").assertSingle()

            // (main klib + framework for Ios) * 3 ios targets (no test sources)
            kotlinNativeCompilationSpans.withAmperModule("ios-app").assertTimes(2 * 3)
        }
    }

    @Test
    fun `compose-android_signing`() = runSlowTest {
        val bundleAndroidTask = ":compose-android:bundleAndroid"
        val result = runCli(
            projectRoot = exampleProject("compose-android"),
            "task",
            bundleAndroidTask,
            configureAndroidHome = true,
        )
        val apk = result.getTaskOutputPath(bundleAndroidTask) / "gradle-project-release.aab"
        JarFile(apk.toFile()).use {
            assertTrue(it.getEntry("META-INF/KEYALIAS.RSA").size > 0)
        }
    }

    @Test
    fun `compose-multiplatform_r8`() = runSlowTest {
        val result = runCli(
            projectRoot = exampleProject("compose-multiplatform"),
            "task", ":android-app:bundleAndroid",
            configureAndroidHome = true,
        )
        assertTrue {
            // FIXME: R8 config is not placed in the actual build directory, but rather relative to the project dir.
            (result.projectRoot / "build" / "temp" / "full-r8-config.txt").exists()
        }
    }

    @Test
    @MacOnly
    fun `compose-multiplatform_apple-tests`() = runSlowTest {
        runCli(exampleProject("compose-multiplatform"), "test", "-p", "iosSimulatorArm64")
    }

    @Test
    fun `compose-multiplatform_buildAndroidDebug`() = runSlowTest {
        runCli(
            projectRoot = exampleProject("compose-multiplatform"),
            "task",
            ":android-app:buildAndroidDebug",
            configureAndroidHome = true,
        )
    }

    @Test
    fun `compose-desktop`() = runSlowTest {
        val projectRoot = exampleProject("compose-desktop")
        with(runCli(projectRoot, "show", "tasks")) {
            jvmAppTasks.forEach { assertContains(stdout, ":compose-desktop:$it") }
        }

        val result = runCli(projectRoot, "build")
        result.readTelemetrySpans().assertKotlinJvmCompilationSpan {
            hasCompilerArgumentStartingWith("-Xplugin=")
        }
    }

    @Test
    fun jvm() = runSlowTest {
        val projectRoot = exampleProject("jvm")
        with(runCli(projectRoot, "show", "tasks")) {
            jvmAppTasks.forEach { assertContains(stdout, ":jvm:$it") }
        }

        val result = runCli(projectRoot, "run")
        assertContains(result.stdout, "Hello, World!")

        result.withTelemetrySpans {
            // testing some default compiler arguments
            assertKotlinJvmCompilationSpan {
                doesNotHaveCompilerArgument("-language-version")
                doesNotHaveCompilerArgument("-api-version")
                hasCompilerArgument("-Xjdk-release=17")
            }
            assertJavaCompilationSpan {
                hasCompilerArgument("--release", "17")
            }
        }

        with(runCli(projectRoot, "test")) {
            assertContains(stdout, "Test run finished")
            assertContains(stdout, "1 tests successful")
            assertContains(stdout, "0 tests failed")
        }
    }

    @Test
    fun `compose-android`() = runSlowTest {
        val projectRoot = exampleProject("compose-android")
        val result1 = runCli(
            projectRoot = projectRoot,
            "show",
            "tasks",
            configureAndroidHome = true,
        )
        androidAppTasks.forEach { assertContains(result1.stdout, ":compose-android:$it") }

        val result2 = runCli(
            projectRoot = projectRoot,
            "build", "--variant=debug", "--variant=release",
            configureAndroidHome = true,
        )
        result2.readTelemetrySpans().kotlinJvmCompilationSpans.assertTimes(2) // debug + release
    }

    @Test
    fun `new-project-template`() = runSlowTest {
        val projectRoot = exampleProject("new-project-template")
        // TODO Assert output
        runCli(projectRoot, "run")
        runCli(projectRoot, "test")
    }
    
    @Test
    fun `spring-petclinic`() = runSlowTest {
        val projectRoot = exampleProject("spring-petclinic")

        // TODO: enable assertEmptyStdErr when AMPER-4256 is fixed
        with(runCli(projectRoot, "test", assertEmptyStdErr = false)) {
            assertContains(stdout, "Test run finished")
            assertContains(stdout, "tests successful")
            assertContains(stdout, "0 tests failed")
        }
    }
    
    @Test
    fun `spring-petclinic-kotlin`() = runSlowTest {
        val projectRoot = exampleProject("spring-petclinic-kotlin")

        // TODO: enable assertEmptyStdErr when AMPER-4265 is fixed
        with(runCli(projectRoot, "test", assertEmptyStdErr = false)) {
            assertContains(stdout, "Test run finished")
            assertContains(stdout, "tests successful")
            assertContains(stdout, "0 tests failed")
        }
    }
}

private val jvmBaseTasks = listOf("compileJvm", "resolveDependenciesJvm")
private val jvmTestTasks = listOf("compileJvmTest", "resolveDependenciesJvmTest")
private val jvmAppTasks = jvmBaseTasks + listOf("runJvm")

private val iosLibraryTasks = listOf(
    "compileIosArm64",
    "compileIosSimulatorArm64",
    "compileIosX64",
)

private val iosAppTasks = iosLibraryTasks + listOf(
    "frameworkIosArm64",
    "frameworkIosSimulatorArm64",
    "frameworkIosX64",

    "buildIosAppIosArm64",
    "buildIosAppIosSimulatorArm64",
    "buildIosAppIosX64",

    "runIosAppIosSimulatorArm64",
    "runIosAppIosX64",

    "testIosSimulatorArm64",
    "testIosX64",
)

private val androidTestTasks = listOf(
    "compileAndroidTestDebug",
    "compileAndroidTestRelease",
)

private val androidBaseTasks = listOf(
    "compileAndroidDebug",
    "compileAndroidRelease",
    "installEmulatorAndroid",
    "installPlatformAndroid",
    "installPlatformToolsAndroid",
    "buildAndroidDebug",
    "buildAndroidRelease",
    "prepareAndroidDebug",
    "prepareAndroidRelease",
    "resolveDependenciesAndroid",
)

private val androidAppTasks = androidBaseTasks + listOf(
    "runAndroidDebug",
    "runAndroidRelease",
)