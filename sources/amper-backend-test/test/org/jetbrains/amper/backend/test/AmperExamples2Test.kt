/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.gradle.tooling.internal.consumer.ConnectorServices
import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.engine.TaskExecutor
import org.jetbrains.amper.test.TestUtil
import org.jetbrains.amper.util.OS
import org.junit.jupiter.api.Assumptions
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

// TODO: review and merged with AmperExamples1Test test suite
// This test was initially testing Gradle-based example projects.
// It was decoupled from the Gradle-based examples, and split into AmperExamples2Test and AmperBasicIntegrationTest.
class AmperExamples2Test : IntegrationTestBase() {
    private val exampleProjectsRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples-standalone")

    private fun setupExampleProject(testProjectName: String): ProjectContext {
        return setupTestProject(exampleProjectsRoot.resolve(testProjectName), copyToTemp = true)
    }

    @Test
    fun jvm() = runTestInfinitely {
        AmperBackend(setupExampleProject("jvm"), backgroundScope).run {
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

        AmperBackend(setupExampleProject("jvm"), backgroundScope).test()
        assertStdoutContains("Test run finished")
        assertStdoutContains("1 tests successful")
        assertStdoutContains("0 tests failed")
    }


    @Test
    fun `compose-multiplatform`() = runTestInfinitely {
        Assumptions.assumeFalse(OS.isWindows, "Skip test on Windows, fix AMPER-525 The process cannot access the file because another process has locked a portion of the file")

        val projectContext = setupExampleProject("compose-multiplatform")
        AmperBackend(projectContext, backgroundScope).run {
            assertHasTasks(
                jvmBaseTasks + jvmTestTasks +
//                      TODO uncomment when  AMPER-526 is fixed
 //                     iosBaseTasks + iosTestTasks +
                        androidBaseTasks + androidTestTasks,
                module = "shared"
            )
            assertHasTasks(androidAppTasks, module = "android-app")
            assertHasTasks(jvmAppTasks, module = "jvm-app")

//          TODO uncomment when  AMPER-526 is fixed
//            assertHasTasks(iosAppTasks, module = "ios-app")
        }

        // TODO handle ios app compilation, currently it fails with this error:
        //   error: could not find 'main' in '<root>' package.
//        AmperBackend(projectContext).compile()
//
//        kotlinJvmCompilationSpans.withAmperModule("shared").assertSingle()
//        kotlinJvmCompilationSpans.withAmperModule("android-app").assertSingle()
//        kotlinJvmCompilationSpans.withAmperModule("jvm-app").assertSingle()
//        kotlinNativeCompilationSpans.withAmperModule("ios-app").assertSingle()
    }

    @Test
    fun composeAndroid() = runTestInfinitely {
        Assumptions.assumeFalse(OS.isWindows, "Skip test on Windows, fix AMPER-525 The process cannot access the file because another process has locked a portion of the file")

        AmperBackend(setupExampleProject("compose-android"), backgroundScope).run {
            assertHasTasks(androidAppTasks)
            compile()
        }
        // debug + release
        kotlinJvmCompilationSpans.assertTimes(2)
        ConnectorServices.reset()
    }

    @Test
    fun composeDesktop() = runTestInfinitely {
        AmperBackend(setupExampleProject("compose-desktop"), backgroundScope).run {
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

private val iosBaseTasks = listOf(
    "compileIosArm64",
    "compileIosSimulatorArm64",
    "compileIosX64",
)
private val iosAppTasks = iosBaseTasks
private val iosTestTasks = listOf(
    "compileIosArm64Test",
    "compileIosSimulatorArm64Test",
    "compileIosX64Test",
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