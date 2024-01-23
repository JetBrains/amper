/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.ProjectContext
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
        projectContext.assertHasJvmApplicationTasks()

        AmperBackend(projectContext).runApplication()
        assertStdoutContains("Hello, World!")
    }

    @Test
    fun `jvm-kotlin+java`() {
        val projectContext = setupExampleProject("jvm-kotlin+java")
        projectContext.assertHasJvmApplicationTasks()

        AmperBackend(projectContext).runApplication()
        assertStdoutContains("Hello, World")

        with(kotlinJvmCompilerSpans.single()) {
            assertKotlinCompilerArgument("-language-version", "1.8")
            assertKotlinCompilerArgument("-jvm-target", "17")
        }
        // TODO add support for Java settings
//        with(javacSpans.single()) {
//            assertJavaCompilerArgument("-source", "17")
//            assertJavaCompilerArgument("-target", "17")
//        }
    }

    @Test
    fun `jvm-with-tests`() {
        val projectContext = setupExampleProject("jvm-with-tests")
        projectContext.assertHasJvmApplicationTasks()

        AmperBackend(projectContext).runApplication()
        assertStdoutContains("Hello, World!")
    }

    private fun ProjectContext.assertHasJvmApplicationTasks() {
        assertHasTasks("compileJvm", "resolveDependenciesJvm", "runJvm")
    }

    private fun ProjectContext.assertHasTasks(vararg tasks: String) {
        AmperBackend(this).showTasks()
        tasks.forEach { task ->
            assertStdoutContains(":${projectRoot.path.name}:$task")
        }
    }
}

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
