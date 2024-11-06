/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.test

import org.jetbrains.amper.test.CliSpanCollector.Companion.runCliTestWithCollector
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.test.TestUtil.runTestInfinitely
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
    fun `compose-multiplatform`() = runCliTestWithCollector {
        with(runCli(projectName, "tasks")) {
            (jvmBaseTasks + jvmTestTasks + iosLibraryTasks + androidTestTasks).forEach {
                assertContains(stdout, ":shared:$it")
            }
            androidAppTasks.forEach { assertContains(stdout, ":android-app:$it") }
            jvmAppTasks.forEach { assertContains(stdout, ":jvm-app:$it") }
            iosAppTasks.forEach { assertContains(stdout, ":ios-app:$it") }
        }

        // TODO Dont compose ios app for non simulator platform, until signing is handled.
        runCli(
            projectName, "build", "-p", "jvm", "-p", "android", "-p", "iosSimulatorArm64",
            assertEmptyStdErr = false,
        )

        // main/test for Jvm + main/test * debug/release for Android.
        kotlinJvmCompilationSpans.withAmperModule("shared").assertTimes(6)

        // debug/release for Android (no test sources).
        kotlinJvmCompilationSpans.withAmperModule("android-app").assertTimes(2)

        // main for Jvm (no test sources).
        kotlinJvmCompilationSpans.withAmperModule("jvm-app").assertSingle()

        // main/test klib + framework for Ios.
        kotlinNativeCompilationSpans.withAmperModule("ios-app").assertTimes(4)
    }

    @Test
    fun `compose-android_signing`() = runTestInfinitely {
        runCli(projectName, "task", ":compose-android:bundleAndroid")
        val apk = tempRoot / "build" / "tasks" / "_compose-android_bundleAndroid" / "gradle-project-release.aab"
        JarFile(apk.toFile()).use {
            assertTrue(it.getEntry("META-INF/KEYALIAS.RSA").size > 0)
        }
    }

    @Test
    fun `compose-multiplatform_r8`() = runTestInfinitely {
        runCli(projectName, "task", ":android-app:bundleAndroid")
        assertTrue {
            val projectRoot = testDataRoot / projectName
            // FIXME: R8 config is not placed in the actual build directory, but rather relative to the project dir.
            (projectRoot / "build" / "temp" / "full-r8-config.txt").exists()
        }
    }

    @Test
    @MacOnly
    fun `compose-multiplatform_apple-tests`() = runTestInfinitely {
         runCli(projectName, "test", "-p", "iosSimulatorArm64")
    }

    @Test
    fun `compose-multiplatform_buildAndroidDebug`() = runTestInfinitely {
        runCli(projectName, "task", ":android-app:buildAndroidDebug")
    }

    @Test
    fun `compose-desktop`() = runCliTestWithCollector {
        with(runCli(projectName, "tasks")) {
            jvmAppTasks.forEach { assertContains(stdout, ":compose-desktop:$it") }
        }

        runCli(projectName, "build")
        assertKotlinJvmCompilationSpan {
            hasCompilerArgumentStartingWith("-Xplugin=")
        }
    }

    @Test
    fun jvm() = runCliTestWithCollector {
        with(runCli(projectName, "tasks")) {
            jvmAppTasks.forEach { assertContains(stdout, ":jvm:$it") }
        }

        with(runCli(projectName, "run")) {
            // testing some default compiler arguments
            assertKotlinJvmCompilationSpan {
                hasCompilerArgument("-language-version", "2.0")
                hasCompilerArgument("-api-version", "2.0")
                hasCompilerArgument("-Xjdk-release=17")
            }
            assertJavaCompilationSpan {
                hasCompilerArgument("--release", "17")
            }
            assertContains(stdout, "Hello, World!")
        }

        with(runCli(projectName, "test")) {
            assertContains(stdout, "Test run finished")
            assertContains(stdout, "1 tests successful")
            assertContains(stdout, "0 tests failed")
        }
    }

    @Test
    fun `compose-android`() = runCliTestWithCollector {
        with(runCli(projectName, "tasks")) {
            androidAppTasks.forEach { assertContains(stdout, ":compose-android:$it") }
        }

        runCli(projectName, "build")
        // debug + release
        kotlinJvmCompilationSpans.assertTimes(2)
    }

    @Test
    @MacOnly
    fun `compose-ios`() = runTestInfinitely {
        // Temporary disable stdErr assertions because linking and xcodebuild produce some warnings
        // that are treated like errors.
        runCli(projectName, "build", "-p", "iosSimulatorArm64", assertEmptyStdErr = false)
        // TODO Can we run it somehow?
    }

    @Test
    fun `new-project-template`() = runTestInfinitely {
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
    "compileIosSimulatorArm64",
)

private val iosAppTasks = iosLibraryTasks + listOf(
    "frameworkIosSimulatorArm64",
    "buildIosAppIosSimulatorArm64",
    "runIosAppIosSimulatorArm64",
    "testIosSimulatorArm64",
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