/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.gradle.tooling.internal.consumer.ConnectorServices
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.tasks.android.AndroidDelegatedGradleTask
import org.jetbrains.amper.test.MacOnly
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.io.path.div
import kotlin.io.path.exists

// TODO: review and merged with AmperExamples1Test test suite
// This test was initially testing Gradle-based example projects.
// It was decoupled from the Gradle-based examples, and split into AmperExamples2Test and AmperBasicIntegrationTest.
class AmperExamples2Test : AmperIntegrationTestBase() {
    private val exampleProjectsRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples-standalone")

    private suspend fun TestCollector.setupExampleProject(testProjectName: String): CliContext =
        setupTestProject(exampleProjectsRoot.resolve(testProjectName), copyToTemp = true)

    @Test
    fun jvm() = runTestWithCollector {
        AmperBackend(setupExampleProject("jvm")).run {
            assertHasTasks(jvmAppTasks)
            runApplication()
        }

        // testing some default compiler arguments
        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "2.0")
            hasCompilerArgument("-api-version", "2.0")
            hasCompilerArgument("-Xjdk-release=17")
        }
        assertJavaCompilationSpan {
            hasCompilerArgument("--release", "17")
        }
        assertStdoutContains("Hello, World!")

        clearTerminalRecording()

        AmperBackend(setupExampleProject("jvm")).test()
        assertStdoutContains("Test run finished")
        assertStdoutContains("1 tests successful")
        assertStdoutContains("0 tests failed")
    }


    @Test
    @MacOnly
    fun `compose-multiplatform`() = runTestWithCollector {
        val projectContext = setupExampleProject("compose-multiplatform")
        AmperBackend(projectContext).run {
            assertHasTasks(jvmBaseTasks + jvmTestTasks + iosLibraryTasks + androidTestTasks, module = "shared")
            assertHasTasks(androidAppTasks, module = "android-app")
            assertHasTasks(jvmAppTasks, module = "jvm-app")
            assertHasTasks(iosAppTasks, module = "ios-app")
        }

        // TODO Dont compose ios app for non simulator platform, until signing is handled.
        val usedPlatforms = setOf(Platform.JVM, Platform.ANDROID, Platform.IOS_SIMULATOR_ARM64)
        AmperBackend(projectContext).build(usedPlatforms)

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
    fun composeAndroid() = runTestWithCollector {
        AmperBackend(setupExampleProject("compose-android")).run {
            assertHasTasks(androidAppTasks)
            build()
        }

        // debug + release
        kotlinJvmCompilationSpans.assertTimes(2)
        ConnectorServices.reset()
    }

    @Test
    fun composeDesktop() = runTestWithCollector {
        AmperBackend(setupExampleProject("compose-desktop")).run {
            assertHasTasks(jvmAppTasks)
            build()
        }

        assertKotlinJvmCompilationSpan {
            hasCompilerArgumentStartingWith("-Xplugin=")
        }
    }

    @Test
    fun composeAndroidSigning() = runTestWithCollector {
        val backend = AmperBackend(setupExampleProject("compose-android"))

        val archive = (backend.runTask("compose-android", "bundleAndroid")
            ?.getOrNull() as? AndroidDelegatedGradleTask.Result)
            ?.artifacts
            ?.getOrNull(0)
            ?.toFile()

        assertNotNull(archive)
        // Verify the signature is in the archive
        JarFile(archive).use { jarFile ->
            assertTrue(jarFile.getEntry("META-INF/KEYALIAS.RSA").size > 0)
        }
        ConnectorServices.reset()
    }
    
    @Test
    fun composeMultiplatformR8() = runTestWithCollector {
        val backend = AmperBackend(setupExampleProject("compose-multiplatform"))
        backend.runTask("android-app", "bundleAndroid")
        assertTrue((backend.context.projectRoot.path / "build" / "temp" / "full-r8-config.txt").exists())
        ConnectorServices.reset()
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
private val androidTestTasks = listOf(
    "compileAndroidTestDebug",
    "compileAndroidTestRelease",
)
