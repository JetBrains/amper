/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.cli.UserReadableError
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AmperExampleProjectsTest : IntegrationTestBase() {

    private val exampleProjectsRoot: Path = TestUtil.amperCheckoutRoot.resolve("examples")

    private fun setupExampleProject(testProjectName: String): ProjectContext {
        val projectContext = setupTestProject(exampleProjectsRoot.resolve(testProjectName), copyToTemp = true)
        projectContext.projectRoot.path.deleteGradleFiles()
        return projectContext
    }

    @Test
    fun `jvm-hello-world`() {
        val projectContext = setupExampleProject("jvm-hello-world")
        projectContext.assertHasTasks(jvmAppTasks)

        AmperBackend(projectContext).runApplication()

        // testing some default compiler arguments
        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.9")
            hasCompilerArgument("-api-version", "1.9")
        }
        assertStdoutContains("Hello, World!")
    }

    @Test
    fun `jvm-kotlin+java`() {
        val projectContext = setupExampleProject("jvm-kotlin+java")
        projectContext.assertHasTasks(jvmAppTasks)

        AmperBackend(projectContext).runApplication()

        assertKotlinJvmCompilationSpan {
            hasCompilerArgument("-language-version", "1.8") // explicit
            hasCompilerArgument("-jvm-target", "17") // explicit
        }
        assertStdoutContains("Hello, World")
        // TODO add support for Java settings
//        with(javacSpans.single()) {
//            assertJavaCompilerArgument("-source", "17")
//            assertJavaCompilerArgument("-target", "17")
//        }
    }

    @Test
    fun `jvm-with-tests`() {
        val projectContext = setupExampleProject("jvm-with-tests")
        projectContext.assertHasTasks(jvmAppTasks + jvmTestTasks)

        AmperBackend(projectContext).runApplication()

        kotlinJvmCompilationSpans.assertSingle()
        assertStdoutContains("Hello, World!")

        resetCollectors()

        val exception = assertFailsWith<UserReadableError> {
            AmperBackend(projectContext).check()
        }
        assertEquals("JVM tests failed for module 'jvm-with-tests' (see errors above)", exception.message)
        assertStdoutContains("MethodSource [className = 'WorldTest', methodName = 'shouldFail', methodParameterTypes = '']")
        assertStdoutContains("=> java.lang.AssertionError: Expected value to be true.")
    }

    @Test
    fun modularized() {
        val projectContext = setupExampleProject("modularized")
        projectContext.assertHasTasks(jvmAppTasks, module = "app")
        projectContext.assertHasTasks(jvmBaseTasks + jvmTestTasks, module = "shared")

        AmperBackend(projectContext).runApplication()

        kotlinJvmCompilationSpans.withAmperModule("app").assertSingle()
        kotlinJvmCompilationSpans.withAmperModule("shared").assertSingle()
        assertStdoutContains("Hello, World!")

        resetCollectors()

        AmperBackend(projectContext).check()
        assertStdoutContains("Test run finished after")
    }

    @Test
    fun multiplatform() {
        val projectContext = setupExampleProject("multiplatform")
        projectContext.assertHasTasks(jvmBaseTasks + jvmTestTasks + iosBaseTasks + iosTestTasks + androidBaseTasks + androidTestTasks, module = "shared")
        projectContext.assertHasTasks(androidAppTasks, module = "android-app")
        projectContext.assertHasTasks(jvmAppTasks, module = "jvm-app")
        projectContext.assertHasTasks(iosAppTasks, module = "ios-app")

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
    fun composeAndroid() {
        val projectContext = setupExampleProject("compose-android")
        projectContext.assertHasTasks(androidAppTasks)
        AmperBackend(projectContext).compile()
        // debug + release
        kotlinJvmCompilationSpans.assertTimes(2)
    }

    @Test
    fun composeDesktop() {
        val projectContext = setupExampleProject("compose-desktop")
        projectContext.assertHasTasks(jvmAppTasks)

        AmperBackend(projectContext).compile()

        assertKotlinJvmCompilationSpan {
            hasCompilerArgumentStartingWith("-Xplugin=")
        }
    }

    private fun ProjectContext.assertHasTasks(tasks: Iterable<String>, module: String? = null) {
        AmperBackend(this).showTasks()
        tasks.forEach { task ->
            assertStdoutContains(":${module ?: projectRoot.path.name}:$task")
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

@OptIn(ExperimentalPathApi::class)
private fun Path.deleteGradleFiles() {
    walk()
        .filter { it.name in knownGradleFiles }
        .forEach { it.deleteExisting() }
}
