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
import org.jetbrains.amper.test.spans.assertJavaCompilationSpan
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.kotlinJvmCompilationSpans
import org.jetbrains.amper.test.spans.kotlinNativeCompilationSpans
import org.jetbrains.amper.test.spans.withAmperModule
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class ExamplesStandaloneTest: AmperCliTestBase() {

    private val examplesStandaloneDir = Dirs.amperCheckoutRoot.resolve("examples-standalone")

    private fun exampleProject(name: String): Path = examplesStandaloneDir.resolve(name)

    @Test
    fun `all examples are covered`() {
        val methods = javaClass.declaredMethods.map { it.name.substringBefore("_") }.toSet()

        val exampleProjects = examplesStandaloneDir.listDirectoryEntries().filter { it.isDirectory() }
        for (entry in exampleProjects) {
            assertContains(methods, entry.name, "Example '${entry.pathString}' is not covered by test '${javaClass.name}'. " +
                    "Please add a test method named '${entry.name}'")
        }
    }

    @Test
    @MacOnly
    fun `compose-multiplatform`() = runSlowTest {
        val tasksResult = runCli(
            projectRoot = exampleProject("compose-multiplatform"),
            "show", "tasks",
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
            assertEmptyStdErr = false,
        )
        buildResult.withTelemetrySpans {
            // main/test for Jvm + main/test * debug for Android.
            kotlinJvmCompilationSpans.withAmperModule("shared").assertTimes(4)

            // debug for Android (no test sources).
            kotlinJvmCompilationSpans.withAmperModule("android-app").assertTimes(1)

            // main for Jvm (no test sources).
            kotlinJvmCompilationSpans.withAmperModule("jvm-app").assertSingle()

            // (main/test klib + framework for Ios) * 3 ios targets
            kotlinNativeCompilationSpans.withAmperModule("ios-app").assertTimes(4 * 3)
        }
    }

    @Test
    fun `compose-android_signing`() = runSlowTest {
        val bundleAndroidTask = ":compose-android:bundleAndroid"
        val result = runCli(exampleProject("compose-android"), "task", bundleAndroidTask)
        val apk = result.getTaskOutputPath(bundleAndroidTask) / "gradle-project-release.aab"
        JarFile(apk.toFile()).use {
            assertTrue(it.getEntry("META-INF/KEYALIAS.RSA").size > 0)
        }
    }

    @Test
    fun `compose-multiplatform_r8`() = runSlowTest {
        val result = runCli(exampleProject("compose-multiplatform"), "task", ":android-app:bundleAndroid")
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
        runCli(exampleProject("compose-multiplatform"), "task", ":android-app:buildAndroidDebug")
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
                hasCompilerArgument("-language-version", "2.1")
                hasCompilerArgument("-api-version", "2.1")
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
        with(runCli(projectRoot, "show", "tasks")) {
            androidAppTasks.forEach { assertContains(stdout, ":compose-android:$it") }
        }

        val result = runCli(projectRoot, "build", "--variant", "debug", "--variant", "release")
        result.readTelemetrySpans().kotlinJvmCompilationSpans.assertTimes(2) // debug + release
    }

    @Test
    @MacOnly
    fun `compose-ios`() = runSlowTest {
        // Temporary disable stdErr assertions because linking and xcodebuild produce some warnings
        // that are treated like errors.
        runCli(
            projectRoot = exampleProject("compose-ios"),
            "build", "-p", "iosSimulatorArm64",
            assertEmptyStdErr = false,
            copyToTempDir = true,
        )
        // TODO Can we run it somehow?
    }

    @Test
    fun `new-project-template`() = runSlowTest {
        val projectRoot = exampleProject("new-project-template")
        runCli(projectRoot, "build")
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

    @Test
    fun `ktor-simplest-sample`() = runSlowTest {
        val projectRoot = exampleProject("ktor-simplest-sample")

        runCli(projectRoot, "build")
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