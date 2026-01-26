/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.tasks.AllRunSettings
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.fail
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class AmperBackendTest : AmperIntegrationTestBase() {

    private fun TestCollector.setupTestDataProject(
        testProjectName: String,
        copyToTemp: Boolean = false,
    ): AmperBackend {
        val cliContext = setupTestProject(
            testProjectPath = Dirs.amperTestProjectsRoot.resolve(testProjectName),
            copyToTemp = copyToTemp,
        )
        val problemReporter = CollectingProblemReporter()
        val model = with(problemReporter) { 
            cliContext.projectContext.readProjectModel(pluginData = emptyList(), mavenPluginXmls = emptyList()) 
        }
        if (problemReporter.problems.isNotEmpty()) {
            fail("Error(s) in the '$testProjectName' test project's model:\n${problemReporter.problems.joinToString("\n") { it.message }}")
        }
        return AmperBackend(
            context = cliContext,
            model = model,
            runSettings = AllRunSettings(),
            backgroundScope = backgroundScope,
        )
    }

    @Test
    fun `jvm transitive dependencies`() = runTestWithCollector {
        val backend = setupTestDataProject("jvm-transitive-dependencies")

        // 1. Check compile classpath
        val result = backend.runTask(TaskName(":app:resolveDependenciesJvm"))
        assertIs<ResolveExternalDependenciesTask.Result>(result)

        // Comparing the lists since the order of libraries on classpath is important
        assertEquals(
            listOf(
                // 1. At first, follow direct dependencies of exported module dependencies
                // exported dependency of D1_exp (app -> D1_exp)
                "picocli-4.7.6.jar",
                // exported dependency of E1_exp (app -> E1_exp)
                "checker-qual-3.44.0.jar",
                // 2. Then, direct dependencies of not-exported module dependencies
                // exported dependency of B1 (app -> B1)
                "lombok-1.18.32.jar",
                // exported dependency of C1 (app -> C1)
                "tensorflow-lite-api-2.16.1.aar",
                // 3. Then, direct dependencies of exported module dependencies of the second layer (keeping order of the first layer)
                // exported dependency of E1_exp (app -> E1_exp -> E2_exp)
                "jackson-core-2.17.1.jar",
                // exported dependency of C2_exp (app -> C1 -> C2_exp)
                "simple-xml-safe-2.7.1.jar",
                // 3. Then, direct dependencies of exported module dependencies of the third layer
                "hamcrest-2.2.jar",
        ),
            result.compileClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved compile dependencies"
        )

        // 2. Check runtime classpath composed after compilation tasks are finished
        val runtimeClasspathResult = backend.runTask(TaskName(":app:runtimeClasspathJvm"))
        assertIs<JvmRuntimeClasspathTask.Result>(runtimeClasspathResult)

        val runtimeClassPath = runtimeClasspathResult.jvmRuntimeClasspath

        val expectedRuntimeClasspath = listOf(
            // 1. At first, follow direct dependencies of exported module dependencies:
            // dependencies of D1_exp (app -> D1_exp)
            "picocli-4.7.6.jar", "osgi.annotation-8.1.0.jar",
            // dependencies of E1_exp (app -> E1_exp)
            "checker-qual-3.44.0.jar", "jakarta.annotation-api-3.0.0.jar",
            // 2. Then, direct dependencies of not-exported module dependencies:
            // dependencies of B1 (app -> B1)
            "lombok-1.18.32.jar", "jcharset-2.1.jar",
            // dependencies of C1 (app -> C1)
            "tensorflow-lite-api-2.16.1.aar", "slf4j-api-2.0.13.jar",
            // 3. Then, direct dependencies of the exported module dependencies (the 2-nd layer, keeping order of the first layer):
            // dependencies of D2 (app -> D1_exp -> D2)
            "asm-9.7.jar", /*"annotations-24.1.0.jar",*/
            // dependencies of E2_exp (app -> E1_exp -> E2_exp)
            "jackson-core-2.17.1.jar", "objenesis-test-3.4.jar",
            // dependencies of B2 (app -> B1 -> B2)
            "tinylog-api-2.7.0.jar", "commons-text-1.12.0.jar",
            // dependencies of C2_exp (app -> C1 -> C2_exp)
            "simple-xml-safe-2.7.1.jar", "jakarta.activation-api-2.1.3.jar",
            // 4. Then, direct dependencies of exported module dependencies (the 4-th layer):
            // dependencies of (app -> B1 -> B2 -> "commons-text-1.12.0.jar")
            "commons-lang3-3.14.0.jar",
            // dependencies of C3_exp (app -> C1 -> C2_exp -> C3_exp)
            "hamcrest-2.2.jar", "apiguardian-api-1.1.2.jar",
        )

        assertEquals(
            expectedRuntimeClasspath,
            result.runtimeClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved direct runtime dependencies of JVM app module"
        )

        assertEquals(
            expectedRuntimeClasspath,
            runtimeClassPath
                .withoutImplicitAmperLibs()
                // filtering out module compile result
                .filterNot { it.startsWith(backend.context.buildOutputRoot.path) }
                .map { it.name },
            "Unexpected list of resolved runtime dependencies"
        )

        val runtimeClasspathViaTask = backend.runTask(TaskName(":app:runtimeClasspathJvm"))
        assertIs<JvmRuntimeClasspathTask.Result>(runtimeClasspathViaTask)

        // Check correct module order in runtime classpath
        val modules = listOf("app", "D1_exp", "E1_exp", "B1", "C1", "D2", "E2_exp", "B2", "C2_exp", "C3_exp").map { "$it-jvm.jar"}

        assertEquals(
            modules + expectedRuntimeClasspath,
            runtimeClasspathViaTask.jvmRuntimeClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved runtime dependencies (via task)"
        )
    }

    @Test
    fun `jvm runtime classpath conflict resolution`() = runTestWithCollector {
        val backend = setupTestDataProject("jvm-runtime-classpath-conflict-resolution")

        val result = backend.runTask(TaskName(":B2:resolveDependenciesJvm")) as ResolveExternalDependenciesTask.Result
        assertIs<ResolveExternalDependenciesTask.Result>(result)

        // should be only one version of commons-io, the highest version
        assertEquals(
            listOf("commons-io-2.16.1.jar"),
            result.runtimeClasspath.withoutImplicitAmperLibs().map { it.name },
            "Unexpected list of resolved runtime dependencies"
        )
    }

    private fun List<Path>.withoutImplicitAmperLibs() =
        filterNot { it.name.startsWith("kotlin-stdlib-") || it.name.startsWith("annotations-") }

    @Disabled("Metadata compilation doesn't 100% work at the moment, because we need DR to support multi-platform dependencies")
    @Test
    fun `simple multiplatform cli metadata`() = runTestWithCollector {
        val backend = setupTestDataProject("simple-multiplatform-cli")

        val compileMetadataJvmMain = TaskName(":shared:compileMetadataJvm")
        backend.runTask(compileMetadataJvmMain)
    }
}
