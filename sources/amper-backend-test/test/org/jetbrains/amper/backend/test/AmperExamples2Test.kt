/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import kotlinx.coroutines.CoroutineScope
import org.gradle.tooling.internal.consumer.ConnectorServices
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test

// TODO: review and merged with AmperExamples1Test test suite
// This test was initially testing Gradle-based example projects.
// It was decoupled from the Gradle-based examples, and split into AmperExamples2Test and AmperBasicIntegrationTest.
class AmperExamples2Test : AmperIntegrationTestBase() {
    private val exampleProjectsRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples-standalone")

    private fun setupExampleProject(testProjectName: String, backgroundScope: CoroutineScope): ProjectContext {
        return setupTestProject(exampleProjectsRoot.resolve(testProjectName), copyToTemp = true, backgroundScope = backgroundScope)
    }

    @Test
    fun jvm() = runTestInfinitely {
        AmperBackend(setupExampleProject("jvm", backgroundScope = backgroundScope)).run {
            assertHasTasks(jvmAppTasks)
            runApplication()
        }

        // testing some default compiler arguments
        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
            hasCompilerArgument("-api-version", "1.9")
            hasCompilerArgument("-Xjdk-release=17")
        }
        assertJavaCompilationSpan {
            hasCompilerArgument("--release", "17")
        }
        assertStdoutContains("Hello, World!")

        resetCollectors()

        AmperBackend(setupExampleProject("jvm", backgroundScope = backgroundScope)).test()
        assertStdoutContains("Test run finished")
        assertStdoutContains("1 tests successful")
        assertStdoutContains("0 tests failed")
    }


    @Test
    fun `compose-multiplatform`() = runTestInfinitely {
        val projectContext = setupExampleProject("compose-multiplatform", backgroundScope = backgroundScope)
        AmperBackend(projectContext).run {
            assertHasTasks(
                jvmBaseTasks + jvmTestTasks +
                        iosLibraryTasks +
                        androidBaseTasks + androidTestTasks,
                module = "shared"
            )
            assertHasTasks(androidAppTasks, module = "android-app")
            assertHasTasks(jvmAppTasks, module = "jvm-app")
            assertHasTasks(iosAppTasks, module = "ios-app")
        }

        // TODO Dont compose ios app for non simulator platform, until signing is handled.
        val usedPlatforms = setOf(Platform.JVM, Platform.ANDROID, Platform.IOS_SIMULATOR_ARM64)
        AmperBackend(projectContext).compile(usedPlatforms)

        // main/test for Jvm + main/test * debug/release for Android.
        kotlinJvmCompilationSpans.withAmperModule("shared").assertTimes(6)

        // debug/release for Android (no test sources).
        kotlinJvmCompilationSpans.withAmperModule("android-app").assertTimes(2)

        // main for Jvm (no test sources).
        kotlinJvmCompilationSpans.withAmperModule("jvm-app").assertSingle()

        // library + framework for Ios.
        kotlinNativeCompilationSpans.withAmperModule("ios-app").assertTimes(2)
    }

    @Test
    fun composeAndroid() = runTestInfinitely {
        AmperBackend(setupExampleProject("compose-android", backgroundScope = backgroundScope)).run {
            assertHasTasks(androidAppTasks)
            compile()
        }
        // debug + release
        kotlinJvmCompilationSpans.assertTimes(2)
        ConnectorServices.reset()
    }

    @Test
    fun composeDesktop() = runTestInfinitely {
        AmperBackend(setupExampleProject("compose-desktop", backgroundScope = backgroundScope)).run {
            assertHasTasks(jvmAppTasks)
            compile()
        }

        assertKotlinJvmCompilationSpan {
            hasCompilerArgumentStartingWith("-Xplugin=")
        }
    }

    private fun AmperBackend.assertHasTasks(tasks: Iterable<String>, module: String? = null) {
        showTasks()
        tasks.forEach { task ->
            assertStdoutContains(":${module ?: context.projectRoot.path.name}:$task")
        }
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

private val knownGradleFiles = setOf(
    "gradle-wrapper.jar",
    "gradle-wrapper.properties",
    "gradle.properties",
    "gradlew",
    "gradlew.bat",
    "build.gradle.kts",
    "settings.gradle.kts",
)