/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NoOpCollectingProblemReporter
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.UnresolvedMavenDependencyNode
import org.jetbrains.amper.dependency.resolution.message
import org.jetbrains.amper.dependency.resolution.orUnspecified
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.collectBuildProblems
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.DependencyBuildProblem

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import kotlin.contracts.ExperimentalContracts
import kotlin.test.assertEquals

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
                expected = """module:shared
                    |+--- shared:common:org.jetbrains.compose.foundation:foundation:12.12.12
                    ||    \--- org.jetbrains.compose.foundation:foundation:12.12.12
                    |+--- shared:common:org.jetbrains.compose.material3:material3:12.12.12
                    ||    \--- org.jetbrains.compose.material3:material3:12.12.12
                    |+--- shared:common:org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                    ||    \--- org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                    |+--- shared:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    ||         \--- org.jetbrains:annotations:13.0
                    |+--- shared:common:org.jetbrains.compose.runtime:runtime:12.12.12
                    ||    \--- org.jetbrains.compose.runtime:runtime:12.12.12
                    |+--- shared:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    |+--- shared:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
                    ||         +--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
                    ||         |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    ||         \--- junit:junit:4.13.2
                    ||              \--- org.hamcrest:hamcrest-core:1.3
                    |+--- shared:commonTest:org.jetbrains.compose.runtime:runtime:12.12.12
                    ||    \--- org.jetbrains.compose.runtime:runtime:12.12.12
                    |+--- shared:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    |+--- shared:jvm:org.jetbrains.compose.runtime:runtime:12.12.12
                    ||    \--- org.jetbrains.compose.runtime:runtime:12.12.12
                    |+--- shared:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    |+--- shared:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)
                    |\--- shared:jvmTest:org.jetbrains.compose.runtime:runtime:12.12.12
                    |     \--- org.jetbrains.compose.runtime:runtime:12.12.12""".trimMargin(),
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
                    "hamcrest-core-1.3.jar",
                    "junit-4.13.2.jar",
                    "kotlin-stdlib-${UsedVersions.kotlinVersion}.jar",
                    "kotlin-test-${UsedVersions.kotlinVersion}.jar",
                    "kotlin-test-junit-${UsedVersions.kotlinVersion}.jar",
                ),
                sharedTestFragmentDeps
            )
        }

        val diagnosticsReporter = NoOpCollectingProblemReporter()
        collectBuildProblems(sharedTestFragmentDeps, diagnosticsReporter, Level.Error)
        val buildProblems = diagnosticsReporter.getProblems()

        assertEquals(7, buildProblems.size)

        // direct dependency on a built-in library,
        // a version of the library is taken from settings:compose:version in file module.yaml
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.compose.foundation", "foundation",
            "module.yaml", 16
        )
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.compose.material3", "material3",
            "module.yaml", 16
        )

        // transitive dependency of built-in libraries,
        // a version of the library is taken from settings:compose:version in file module.yaml
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.compose.runtime", "runtime",
            "module.yaml", 16
        )

        // transitive dependency on a built-in library,
        // a version of the library is taken from settings:serialization:version in file template.yaml
        checkBuiltInDependencyBuildProblem(
            buildProblems, "org.jetbrains.kotlinx", "kotlinx-serialization-core",
            "..\\..\\templates\\template.yaml"
                .let { if (DefaultSystemInfo.detect().family != OsFamily.Windows) it.replace("\\", "/") else it },
            5
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
                expected = """Fragment 'jvm-invalid-dependencies.common' dependencies
                    |+--- jvm-invalid-dependencies:common:com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared, unresolved
                    ||    \--- com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared, unresolved
                    |+--- jvm-invalid-dependencies:common:com.fasterxml.     jackson.core:jackson-core:2.17.2, unresolved
                    ||    \--- com.fasterxml.     jackson.core:jackson-core:2.17.2, unresolved
                    |+--- jvm-invalid-dependencies:common:com.fasterx/ml.jackson.core:jackson-core:2.17.2, unresolved
                    ||    \--- com.fasterx/ml.jackson.core:jackson-core:2.17.2, unresolved
                    |+--- jvm-invalid-dependencies:common:com.fasterxml.jackson.core, unresolved
                    ||    \--- com.fasterxml.jackson.core, unresolved
                    |+--- jvm-invalid-dependencies:common:com.fasterxml.jackson.core:jackson-core:jackson-core:jackson-core:jackson-core:2.17.2, unresolved
                    ||    \--- com.fasterxml.jackson.core:jackson-core:jackson-core:jackson-core:jackson-core:2.17.2, unresolved
                    |\--- jvm-invalid-dependencies:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |     \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    |          \--- org.jetbrains:annotations:13.0""".trimMargin(),
                verifyMessages = false
            )
        }

        val diagnosticsReporter = NoOpCollectingProblemReporter()
        collectBuildProblems(commonFragmentDeps, diagnosticsReporter, Level.Error)
        val buildProblems = diagnosticsReporter.getProblems()

        assertEquals(5, buildProblems.size)

        buildProblems.forEach {
            val buildProblem = it as DependencyBuildProblem
            val dependency = buildProblem.problematicDependency as UnresolvedMavenDependencyNode
            val expectedError = when (dependency.coordinates) {
                "com.fasterxml.jackson.core:jackson-core:2.17.2 - ../shared" ->
                    "Maven coordinates should not contain spaces, but got ${dependency.coordinates}"

                "com.fasterxml.     jackson.core:jackson-core:2.17.2" ->
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

    @OptIn(ExperimentalContracts::class)
    internal fun DependencyNode.isMavenDependency(group: String, module: String): Boolean {
        return this is MavenDependencyNode && this.group == group && this.module == module
    }

    private fun assertDependencyError(node: DependencyNode, group: String, module: String): Boolean {
        if (node.isMavenDependency(group, module)) {
            node as MavenDependencyNode
            assertEquals(
                setOf("Unable to resolve dependency ${node.dependency.group}:${node.dependency.module}:${node.dependency.version.orUnspecified()}"),
                node.messages.map { it.text }.toSet()
            )
            return true
        }
        return false
    }

    private fun checkBuiltInDependencyBuildProblem(
        buildProblems: Collection<BuildProblem>, group: String,
        module: String, filePath: String, versionLineNumber: Int?
    ) {
        val relevantBuildProblems = buildProblems.filter {
            it is DependencyBuildProblem
                    && it.problematicDependency.isMavenDependency(group, module)
        }

        relevantBuildProblems.forEach { buildProblem ->
            buildProblem as DependencyBuildProblem
            val mavenDependency = (buildProblem.problematicDependency as MavenDependencyNode).dependency

            assertEquals(
                setOf(
                    "Unable to resolve dependency ${mavenDependency.group}:${mavenDependency.module}:${mavenDependency.version.orUnspecified()}" +
                            (if (versionLineNumber != null) ". The version ${mavenDependency.version} is defined at $filePath:$versionLineNumber" else "")
                ),
                setOf(buildProblem.errorMessage.text)
            )
        }
    }
}
