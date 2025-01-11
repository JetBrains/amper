/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.collectSpansFromCli
import org.jetbrains.amper.test.spans.assertJavaCompilationSpan
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.kotlinJvmCompilationSpans
import org.jetbrains.amper.test.spans.kotlinNativeCompilationSpans
import org.jetbrains.amper.test.spans.withAmperModule
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInfo
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

class AmperExamplesStandaloneTest: AmperCliTestBase() {

    override val testDataRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples-standalone")

    lateinit var projectName: String

    @BeforeEach
    fun determineTestProjectName(testInfo: TestInfo) {
        projectName = testInfo.testMethod.get().name.substringBefore("_")
    }

    @Test
    fun `all examples are covered`() {
        val methods = javaClass.declaredMethods.map { it.name.substringBefore("_") }.toSet()

        for (entry in testDataRoot.listDirectoryEntries().filter { it.isDirectory() }) {
            assertContains(methods, entry.name, "Example '${entry.pathString}' is not covered by test '${javaClass.name}'. " +
                    "Please add a test method named '${entry.name}'")
        }
    }

    @Test
    @MacOnly
    fun `compose-multiplatform`() = runSlowTest {
        val amperResult = runCliInTempDir(projectName, "tasks")
        with(amperResult) {
            (jvmBaseTasks + jvmTestTasks + iosLibraryTasks + androidTestTasks).forEach {
                assertContains(stdout, ":shared:$it")
            }
            androidAppTasks.forEach { assertContains(stdout, ":android-app:$it") }
            jvmAppTasks.forEach { assertContains(stdout, ":jvm-app:$it") }
            iosAppTasks.forEach { assertContains(stdout, ":ios-app:$it") }
        }

        collectSpansFromCli {
            runCli(
                projectRoot = amperResult.projectRoot,
                "build",
                assertEmptyStdErr = false,
            )
        }.run {
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
        runCli(projectName, "task", ":compose-android:bundleAndroid")
        val apk = tempRoot / "build" / "tasks" / "_compose-android_bundleAndroid" / "gradle-project-release.aab"
        JarFile(apk.toFile()).use {
            assertTrue(it.getEntry("META-INF/KEYALIAS.RSA").size > 0)
        }
    }

    @Test
    fun `compose-multiplatform_r8`() = runSlowTest {
        runCli(projectName, "task", ":android-app:bundleAndroid")
        assertTrue {
            val projectRoot = testDataRoot / projectName
            // FIXME: R8 config is not placed in the actual build directory, but rather relative to the project dir.
            (projectRoot / "build" / "temp" / "full-r8-config.txt").exists()
        }
    }

    @Test
    @MacOnly
    fun `compose-multiplatform_apple-tests`() = runSlowTest {
        runCli(projectName, "test", "-p", "iosSimulatorArm64")
    }

    @Test
    fun `compose-multiplatform_buildAndroidDebug`() = runSlowTest {
        runCli(projectName, "task", ":android-app:buildAndroidDebug")
    }

    @Test
    fun `compose-desktop`() = runSlowTest {
        with(runCli(projectName, "tasks")) {
            jvmAppTasks.forEach { assertContains(stdout, ":compose-desktop:$it") }
        }

        collectSpansFromCli {
            runCli(projectName, "build")
        }.assertKotlinJvmCompilationSpan {
            hasCompilerArgumentStartingWith("-Xplugin=")
        }
    }

    @Test
    fun jvm() = runSlowTest {
        with(runCli(projectName, "tasks")) {
            jvmAppTasks.forEach { assertContains(stdout, ":jvm:$it") }
        }

        collectSpansFromCli {
            val result = runCli(projectName, "run")
            assertContains(result.stdout, "Hello, World!")
        }.run {
            // testing some default compiler arguments
            assertKotlinJvmCompilationSpan {
                hasCompilerArgument("-language-version", "2.0")
                hasCompilerArgument("-api-version", "2.0")
                hasCompilerArgument("-Xjdk-release=17")
            }
            assertJavaCompilationSpan {
                hasCompilerArgument("--release", "17")
            }
        }

        with(runCli(projectName, "test")) {
            assertContains(stdout, "Test run finished")
            assertContains(stdout, "1 tests successful")
            assertContains(stdout, "0 tests failed")
        }
    }

    @Test
    fun `compose-android`() = runSlowTest {
        with(runCli(projectName, "tasks")) {
            androidAppTasks.forEach { assertContains(stdout, ":compose-android:$it") }
        }

        collectSpansFromCli {
            runCli(projectName, "build", "--variant", "debug", "--variant", "release")
        }.run {
            // debug + release
            kotlinJvmCompilationSpans.assertTimes(2)
        }
    }

    @Test
    @MacOnly
    fun `compose-ios`() = runSlowTest {
        // Temporary disable stdErr assertions because linking and xcodebuild produce some warnings
        // that are treated like errors.
        runCliInTempDir(projectName, "build", "-p", "iosSimulatorArm64", assertEmptyStdErr = false)
        // TODO Can we run it somehow?
    }

    @Test
    fun `new-project-template`() = runSlowTest {
        runCli(projectName, "build")
        // TODO Assert output
        runCli(projectName, "run")
        runCli(projectName, "test")
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