/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.reflect.full.declaredFunctions
import kotlin.test.assertEquals
import kotlin.test.fail

class GradleExamplesTest : E2ETestFixture("../../examples-gradle/",
    runWithPluginClasspath = if (System.getenv("WITH_PLUGIN_CLASSPATH") != null) System.getenv("WITH_PLUGIN_CLASSPATH").toBoolean() else true  ) {
    @Test
    fun `check all example projects are tested`() {
        @OptIn(ExperimentalPathApi::class)
        val testProjects = Path.of(pathToProjects).walk().filter {
            it.name.startsWith("settings.gradle", ignoreCase = true)
        }.map { it.parent }.sorted().toList()

        val testMethods = this::class.declaredFunctions.filter {
            it.annotations.any { it.annotationClass.qualifiedName == "org.junit.jupiter.api.Test" }
        }

        val notTested = testProjects.filter { p -> testMethods.none { m -> m.name.contains(p.name) } }

        if(notTested.isNotEmpty()) {
            fail(message = "Some example projects are not tested:\n"
                        + notTested.joinToString("\n") { it.pathString })
        }
    }

    @Test
    fun `new-project-template runs and prints Hello, World`() = test(
        projectName = "new-project-template",
        "run",
        expectOutputToHave = "Hello, World!"
    )

    @Test
    fun `jvm-hello-world runs and prints Hello, World`() = test(
        projectName = "jvm-hello-world",
        "run",
        expectOutputToHave = "Hello, World!"
    )

    @Test
    fun `jvm-kotlin+java runs and prints Hello, World`() = test(
        projectName = "jvm-kotlin+java",
        "run",
        expectOutputToHave = "Hello, World"
    )

    @Test
    fun `jvm-with-tests test task fails`() = test(
        projectName = "jvm-with-tests",
        "test",
        expectOutputToHave = "> There were failing tests. See the report at: file:",
        shouldSucceed = false
    )

    @Test
    fun `modularized test task succeeds`() = test(
        projectName = "modularized",
        "test",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @KonanFolderLock
    fun `multiplatform build task succeeds`() = test(
        projectName = "multiplatform",
        ":jvm-app:build", ":android-app:build", ":ios-app:build",
        expectOutputToHave = listOf("BUILD SUCCESSFUL")
    )

    @Test
    fun `compose-desktop build task`() = test(
        projectName = "compose-desktop",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    @KonanFolderLock
    fun `compose-ios task`() = test(
        projectName = "compose-ios",
        "buildIosAppMain",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `compose-android build task`() = test(
        projectName = "compose-android",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `templates build task`() = test(
        projectName = "templates",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `gradle-interop`() = test(
        projectName = "gradle-interop",
        "build", ":hello", ":run",
        expectOutputToHave = listOf(
            """
            > Task :hello
            Hello from Gradle task!
            """.trimIndent(),

            """
            > Task :run
            Hello, World!
            """.trimIndent(),

            "BUILD SUCCESSFUL"
        )
    )

    @Test
    fun `gradle-migration-jvm`() = test(
        projectName = "gradle-migration-jvm",
        "build", ":app:run",
        expectOutputToHave = listOf("Hello, World!", "BUILD SUCCESSFUL")
    )

    @Test
    fun `gradle-migration-kmp`() = test(
        projectName = "gradle-migration-kmp",
        "build",
        expectOutputToHave = "BUILD SUCCESSFUL"
    )

    @Test
    fun `coverage check task`() = test(
        projectName = "coverage",
        "build", "check",
        expectOutputToHave = listOf(
            "BUILD SUCCESSFUL",
            "> Task :koverXmlReport",
            "> Task :koverVerify",
            "> Task :koverHtmlReport",
            "/report/coverage-report/index.html\n"
        )
    )
}
