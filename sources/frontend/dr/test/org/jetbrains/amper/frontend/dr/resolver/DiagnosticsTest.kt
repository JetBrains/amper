/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NoOpCollectingProblemReporter
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.UnresolvedMavenDependencyNode
import org.jetbrains.amper.dependency.resolution.orUnspecified
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.collectBuildProblems
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.DependencyBuildProblem
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class DiagnosticsTest : BaseModuleDrTest() {

    @Test
    fun `test sync diagnostics`() {
        val aom = getTestProjectModel("multi-module-failed-resolve", testDataRoot)

        assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules.single { it.userReadableName == "shared" }.fragments.map { it.name }.toSet(),
            ""
        )

        val sharedTestFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot)
                ),
                module = "shared",
                expected = """
                    module:shared
                    ├─── shared:common:org.jetbrains.compose.foundation:foundation:12.12.12
                    │    ╰─── org.jetbrains.compose.foundation:foundation:12.12.12
                    ├─── shared:common:org.jetbrains.compose.material3:material3:12.12.12
                    │    ╰─── org.jetbrains.compose.material3:material3:12.12.12
                    ├─── shared:common:org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                    │    ╰─── org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                    ├─── shared:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    │         ╰─── org.jetbrains:annotations:13.0
                    ├─── shared:common:org.jetbrains.compose.runtime:runtime:12.12.12
                    │    ╰─── org.jetbrains.compose.runtime:runtime:12.12.12
                    ├─── shared:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    ├─── shared:commonTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}
                    │         ├─── org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
                    │         │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    │         ╰─── org.junit.jupiter:junit-jupiter-api:5.10.1
                    │              ├─── org.junit:junit-bom:5.10.1
                    │              ├─── org.opentest4j:opentest4j:1.3.0
                    │              ├─── org.junit.platform:junit-platform-commons:1.10.1
                    │              │    ├─── org.junit:junit-bom:5.10.1
                    │              │    ╰─── org.apiguardian:apiguardian-api:1.1.2
                    │              ╰─── org.apiguardian:apiguardian-api:1.1.2
                    ├─── shared:commonTest:org.jetbrains.compose.runtime:runtime:12.12.12
                    │    ╰─── org.jetbrains.compose.runtime:runtime:12.12.12
                    ├─── shared:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    ├─── shared:jvm:org.jetbrains.compose.runtime:runtime:12.12.12
                    │    ╰─── org.jetbrains.compose.runtime:runtime:12.12.12
                    ├─── shared:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    ├─── shared:jvmTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion} (*)
                    ╰─── shared:jvmTest:org.jetbrains.compose.runtime:runtime:12.12.12
                         ╰─── org.jetbrains.compose.runtime:runtime:12.12.12
                    """.trimIndent(),
                messagesCheck = { node ->
                    if (!assertDependencyError(node, "org.jetbrains.compose.foundation", "foundation")
                        && !assertDependencyError(node, "org.jetbrains.compose.material3", "material3")
                        && !assertDependencyError(node, "org.jetbrains.compose.runtime", "runtime")
                        && !assertDependencyError(node, "org.jetbrains.kotlinx", "kotlinx-serialization-core")
                    ) {
                        node.verifyOwnMessages()
                    }
                }
            )
        }

        runBlocking {
            downloadAndAssertFiles(
                listOf(
                    "annotations-13.0.jar",
                    "apiguardian-api-1.1.2.jar",
                    "junit-jupiter-api-5.10.1.jar",
                    "junit-platform-commons-1.10.1.jar",
                    "kotlin-stdlib-${UsedVersions.kotlinVersion}.jar",
                    "kotlin-test-${UsedVersions.kotlinVersion}.jar",
                    "kotlin-test-junit5-${UsedVersions.kotlinVersion}.jar",
                    "opentest4j-1.3.0.jar",
                ),
                sharedTestFragmentDeps
            )
        }

        val diagnosticsReporter = NoOpCollectingProblemReporter()
        collectBuildProblems(sharedTestFragmentDeps, diagnosticsReporter, Level.Error)
        val buildProblems = diagnosticsReporter.getProblems()

        assertEquals(7, buildProblems.size)

        // Direct dependency on a built-in library,
        // A version of the library is taken from settings:compose:version in file module.yaml
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.compose.foundation", "foundation",
            Path("module.yaml"), 16, 5
        )
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.compose.material3", "material3",
        Path("module.yaml"), 16, 5
        )

        // Implicit dependency added by `compose: enabled`
        // A version of the library is taken from settings:compose:version in file module.yaml
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.compose.runtime", "runtime",
            Path("module.yaml")
        )

        // Implicit dependency added by `serialization: enabled`
        // A version of the library is taken from settings:serialization:version in file template.yaml
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.kotlinx", "kotlinx-serialization-core",
            Path("..") / ".." / "templates" / "template.yaml",
            5, 7
        )
    }

    @Test
    fun `test invalid dependency coordinates`() {
        val aom = getTestProjectModel("jvm-invalid-dependencies", testDataRoot)

        assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules.single().fragments.map { it.name }.toSet(),
            ""
        )

        val commonFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot)
                ),
                module = "jvm-invalid-dependencies",
                fragment = "common",
                expected = """
                    Fragment 'jvm-invalid-dependencies.common' dependencies
                    ├─── jvm-invalid-dependencies:common:com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared, unresolved
                    │    ╰─── com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared, unresolved
                    ├─── jvm-invalid-dependencies:common:com.fasterxml.     jackson.core:jackson-core:2.17.2, unresolved
                    │    ╰─── com.fasterxml.     jackson.core:jackson-core:2.17.2, unresolved
                    ├─── jvm-invalid-dependencies:common:com.fasterxml.jackson.core:jackson-core:2.17.2 :exported, unresolved
                    │    ╰─── com.fasterxml.jackson.core:jackson-core:2.17.2 :exported, unresolved
                    ├─── jvm-invalid-dependencies:common:com.fasterx/ml.jackson.core:jackson-core:2.17.2, unresolved
                    │    ╰─── com.fasterx/ml.jackson.core:jackson-core:2.17.2, unresolved
                    ├─── jvm-invalid-dependencies:common:com.fasterxml.jackson.core, unresolved
                    │    ╰─── com.fasterxml.jackson.core, unresolved
                    ├─── jvm-invalid-dependencies:common:com.fasterxml.jackson.core:jackson-core:jackson-core:jackson-core:jackson-core:2.17.2, unresolved
                    │    ╰─── com.fasterxml.jackson.core:jackson-core:jackson-core:jackson-core:jackson-core:2.17.2, unresolved
                    ╰─── jvm-invalid-dependencies:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                         ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                              ╰─── org.jetbrains:annotations:13.0
                """.trimIndent(),
                verifyMessages = false
            )
        }

        val diagnosticsReporter = NoOpCollectingProblemReporter()
        collectBuildProblems(commonFragmentDeps, diagnosticsReporter, Level.Error)
        val buildProblems = diagnosticsReporter.getProblems()

        assertEquals(6, buildProblems.size)

        buildProblems.forEach {
            val buildProblem = it as DependencyBuildProblem
            val dependency = buildProblem.problematicDependency as UnresolvedMavenDependencyNode
            val expectedError = when (dependency.coordinates) {
                "com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared" ->
                    "Maven coordinates should not contain spaces, but got ${dependency.coordinates}"

                "com.fasterxml.     jackson.core:jackson-core:2.17.2",
                "com.fasterxml.jackson.core:jackson-core:2.17.2 :exported" ->
                    "Maven coordinates should not contain spaces, but got ${dependency.coordinates}"

                "com.fasterx/ml.jackson.core:jackson-core:2.17.2" ->
                    "Maven coordinates should not contain parts with slashes, but got ${dependency.coordinates}"

                "com.fasterxml.jackson.core" ->
                    "Maven coordinates should contain at least 3 parts separated by :, but got ${dependency.coordinates}"

                "com.fasterxml.jackson.core:jackson-core:jackson-core:jackson-core:jackson-core:2.17.2" ->
                    "Maven coordinates should contain at most 4 parts separated by :, but got ${dependency.coordinates}"

                else -> fail("Unexpected dependency coordinates: ${dependency.coordinates}")
            }

            assertEquals(expectedError, buildProblem.errorMessage.message)
        }
    }

    // AMPER-4270
    @Test
    fun `overridden version for BOM version is not displayed for unspecified versions`() {
        val aom = getTestProjectModel("jvm-bom-support", testDataRoot)
        val commonDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot)
                ),
                module = "app",
                fragment = "common",
                expected = """
                    Fragment 'app.common' dependencies
                    ├─── app:common:com.fasterxml.jackson.core:jackson-annotations:unspecified
                    │    ╰─── com.fasterxml.jackson.core:jackson-annotations:unspecified -> 2.18.3
                    │         ╰─── com.fasterxml.jackson:jackson-bom:2.18.3
                    │              ╰─── com.fasterxml.jackson.core:jackson-annotations:2.18.3 (c)
                    ├─── app:common:com.fasterxml.jackson:jackson-bom:2.18.3
                    │    ╰─── com.fasterxml.jackson:jackson-bom:2.18.3 (*)
                    ╰─── app:common:org.jetbrains.kotlin:kotlin-stdlib:2.1.20, implicit
                         ╰─── org.jetbrains.kotlin:kotlin-stdlib:2.1.20
                              ╰─── org.jetbrains:annotations:13.0
                """.trimIndent(),
                verifyMessages = false,
            )
        }


        val diagnosticsReporter = NoOpCollectingProblemReporter()
        collectBuildProblems(commonDeps, diagnosticsReporter, Level.Warning)
        val buildProblems = diagnosticsReporter.getProblems()

        assertEquals(
            0, buildProblems.size,
            "No problems should be reported for JVM deps, but got the following ${buildProblems.size} problem(s):\n${
                buildProblems.joinToString(
                    "\n"
                ) { it.message }
            }"
        )
    }

    @OptIn(ExperimentalContracts::class)
    internal fun DependencyNode.isMavenDependency(group: String, module: String): Boolean {
        contract {
            returns(true) implies (this@isMavenDependency is MavenDependencyNode)
        }
        return this is MavenDependencyNode && this.group == group && this.module == module
    }

    private fun assertDependencyError(node: DependencyNode, group: String, module: String): Boolean {
        if (node.isMavenDependency(group, module)) {
            assertEquals(
                setOf("Unable to resolve dependency ${node.dependency.group}:${node.dependency.module}:${node.dependency.version.orUnspecified()}"),
                node.messages.map { it.message }.toSet()
            )
            return true
        }
        return false
    }

    private fun checkBuiltInDependencyBuildProblem(
        buildProblems: Collection<BuildProblem>, group: String,
        module: String, filePath: Path, versionLineNumber: Int? = null, versionColumn: Int? = null
    ) {
        val relevantBuildProblems = buildProblems.filter {
            it is DependencyBuildProblem
                    && it.problematicDependency.isMavenDependency(group, module)
        }

        relevantBuildProblems.forEach { buildProblem ->
            buildProblem as DependencyBuildProblem
            val mavenDependency = (buildProblem.problematicDependency as MavenDependencyNode).dependency

            assertContains(buildProblem.message, "Unable to resolve dependency ${mavenDependency.group}:${mavenDependency.module}:${mavenDependency.version.orUnspecified()}")
            if (versionLineNumber != null) {
                assertContains(buildProblem.message, "The version ${mavenDependency.version} is defined at $filePath:$versionLineNumber:$versionColumn")
            } else {
                assertFalse(buildProblem.message.contains("The version ${mavenDependency.version} is defined at"))
            }
        }
    }
}
