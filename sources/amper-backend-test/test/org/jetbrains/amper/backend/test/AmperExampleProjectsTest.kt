/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
import org.jetbrains.amper.test.TestUtil
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteExisting
import kotlin.io.path.name
import kotlin.io.path.walk
import kotlin.test.Test

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
            hasCompilerArgument("-jvm-target", "17")
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

        // FIXME fix test compilation
//        AmperBackend(projectContext).check()
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

        // FIXME fix test compilation
//        AmperBackend(projectContext).check()
    }

    @Test
    fun multiplatform() {
        val projectContext = setupExampleProject("multiplatform")

        projectContext.assertHasTasks(jvmBaseTasks + jvmTestTasks + iosBaseTasks + iosTestTasks + androidBaseTasks + androidTestTasks, module = "shared")
        projectContext.assertHasTasks(androidAppTasks, module = "android-app")
        projectContext.assertHasTasks(jvmAppTasks, module = "jvm-app")
        projectContext.assertHasTasks(iosAppTasks, module = "ios-app")

        // FIXME compileAndroid task doesn't exist yet defines dependencies
//        AmperBackend(projectContext).compile()
//
//        kotlinJvmCompilationSpans.withAmperModule("shared").assertSingle()
//        kotlinJvmCompilationSpans.withAmperModule("android-app").assertSingle()
//        kotlinJvmCompilationSpans.withAmperModule("jvm-app").assertSingle()
//        kotlinNativeCompilationSpans.withAmperModule("ios-app").assertSingle()
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
    "downloadAndroidEmulator",
    "downloadAndroidSdk",
    "downloadCmdlineTools",
    "downloadPlatformTools",
    "finalizeDebugAndroidBuild",
    "finalizeReleaseAndroidBuild",
    "prepareDebugAndroidBuild",
    "prepareReleaseAndroidBuild",
    "resolveDependenciesAndroid",
)
private val androidAppTasks = androidBaseTasks + listOf(
    "runDebugAndroid",
    "runReleaseAndroid",
)
private val androidTestTasks = listOf(
    "compileAndroidTestDebug",
    "compileAndroidTestRelease",
)

// doesn't contain the Gradle version catalog on purpose, we want to keep it because Amper supports it
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
