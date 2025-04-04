/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */
package org.jetbrains.amper.backend.test

import org.jetbrains.amper.cli.AmperBackend
import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.TaskName
import org.jetbrains.amper.tasks.ResolveExternalDependenciesTask
import org.jetbrains.amper.tasks.jvm.JvmRuntimeClasspathTask
import org.jetbrains.amper.test.Dirs
import org.jetbrains.amper.test.TestCollector
import org.jetbrains.amper.test.TestCollector.Companion.runTestWithCollector
import org.jetbrains.amper.test.WindowsOnly
import org.jetbrains.amper.test.spans.assertEachKotlinJvmCompilationSpan
import org.jetbrains.amper.test.spans.assertEachKotlinNativeCompilationSpan
import org.jetbrains.amper.test.spans.assertKotlinJvmCompilationSpan
import org.junit.jupiter.api.Disabled
import org.tinylog.Level
import java.nio.file.LinkOption
import java.nio.file.Path
import java.util.jar.JarFile
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.walk
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AmperBackendTest : AmperIntegrationTestBase() {

    private suspend fun TestCollector.setupTestDataProject(
        testProjectName: String,
        programArgs: List<String> = emptyList(),
        copyToTemp: Boolean = false,
    ): CliContext = setupTestProject(
        testProjectPath = Dirs.amperTestProjectsRoot.resolve(testProjectName),
        copyToTemp = copyToTemp,
        programArgs = programArgs,
    )

    @Test
    fun `simple multiplatform cli sources jars`() = runTestWithCollector {
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = emptyList())
        val backend = AmperBackend(projectContext)

        val sourcesJarJvm = TaskName(":shared:sourcesJarJvm")
        backend.runTask(sourcesJarJvm)
        val sourcesJarLinuxArm64Task = TaskName(":shared:sourcesJarLinuxArm64")
        backend.runTask(sourcesJarLinuxArm64Task)
        val sourcesJarLinuxX64Task = TaskName(":shared:sourcesJarLinuxX64")
        backend.runTask(sourcesJarLinuxX64Task)
        val sourcesJarMingwX64Task = TaskName(":shared:sourcesJarMingwX64")
        backend.runTask(sourcesJarMingwX64Task)
        val sourcesJarMacosArm64Task = TaskName(":shared:sourcesJarMacosArm64")
        backend.runTask(sourcesJarMacosArm64Task)

        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarJvm) / "shared-jvm-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "jvmMain/",
                "jvmMain/Jvm.java",
                "jvmMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarLinuxArm64Task) / "shared-linuxarm64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "linuxMain/",
                "linuxMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarLinuxX64Task) / "shared-linuxx64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "linuxMain/",
                "linuxMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarMingwX64Task) / "shared-mingwx64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "mingwMain/",
                "mingwMain/World.kt",
            )
        )
        assertJarFileEntries(
            jarPath = backend.context.taskOutputPath(sourcesJarMacosArm64Task) / "shared-macosarm64-sources.jar",
            expectedEntries = listOf(
                "META-INF/MANIFEST.MF",
                "commonMain/",
                "commonMain/World.kt",
                "commonMain/program1.kt",
                "commonMain/program2.kt",
                "macosMain/",
                "macosMain/World.kt",
            )
        )
    }

    @Test
    fun `jvm transitive dependencies`() = runTestWithCollector {
        val projectContext = setupTestDataProject("jvm-transitive-dependencies")

        // 1. Check compile classpath
        val result = AmperBackend(projectContext)
            .runTask(TaskName(":app:resolveDependenciesJvm"))
            ?.getOrNull() as? ResolveExternalDependenciesTask.Result

        assertNotNull(result, "unexpected result absence for :app:resolveDependenciesJvm")

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
        val runtimeClasspathResult = AmperBackend(projectContext)
                .runTask(TaskName(":app:runtimeClasspathJvm"))
                ?.getOrNull() as? JvmRuntimeClasspathTask.Result

        assertNotNull(runtimeClasspathResult, "unexpected result absence for :app:runtimeClasspathJvm")

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
                .filterNot { it.startsWith(projectContext.buildOutputRoot.path) }
                .map { it.name },
            "Unexpected list of resolved runtime dependencies"
        )

        val runtimeClasspathViaTask = AmperBackend(projectContext)
            .runTask(TaskName(":app:runtimeClasspathJvm"))
            ?.getOrNull() as? JvmRuntimeClasspathTask.Result

        assertNotNull(runtimeClasspathViaTask, "unexpected result absence for :app:runtimeClasspathJvm")

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
        val projectContext = setupTestDataProject("jvm-runtime-classpath-conflict-resolution")

        val result = AmperBackend(projectContext)
            .runTask(TaskName(":B2:resolveDependenciesJvm"))
            ?.getOrNull() as? ResolveExternalDependenciesTask.Result
            ?: error("unexpected result absence for :B2:resolveDependenciesJvm")

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
        val projectContext = setupTestDataProject("simple-multiplatform-cli", programArgs = emptyList())
        val backend = AmperBackend(projectContext)

        val compileMetadataJvmMain = TaskName(":shared:compileMetadataJvm")
        backend.runTask(compileMetadataJvmMain)
    }
}

private fun assertJarFileEntries(jarPath: Path, expectedEntries: List<String>) {
    assertTrue(jarPath.existsCaseSensitive(), "Jar file $jarPath doesn't exist")
    JarFile(jarPath.toFile()).use { jar ->
        assertEquals(expectedEntries, jar.entries().asSequence().map { it.name }.toList())
    }
}

private fun Path.existsCaseSensitive(): Boolean =
    // toRealPath() ensures the case matches what's on disk even on Windows
    exists() && absolute().normalize().pathString == toRealPath(LinkOption.NOFOLLOW_LINKS).pathString

// This is not a public API, we're not supposed to know where the outputs are, but it's convenient to test
// if the task output is correct. We might remove it later when the output of the tested tasks are actually
// tested with their real-life usage. For instance, source jars will be tested as part of publication.
private fun CliContext.taskOutputPath(taskName: TaskName): Path =
    buildOutputRoot.path / "tasks" / taskName.name.replace(":", "_")
