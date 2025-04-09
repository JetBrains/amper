/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.dependency.resolution.MavenDependencyConstraintNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.dependency.resolution.originalVersion
import org.jetbrains.amper.dependency.resolution.resolvedVersion
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DependencyInsightsTest : BaseModuleDrTest() {
    override val testGoldenFilesRoot: Path = super.testGoldenFilesRoot / "dependencyInsights"

    @Test
    fun `test sync empty jvm module`() {
        val aom = getTestProjectModel("jvm-empty", testDataRoot)

        assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules[0].fragments.map { it.name }.toSet(),
            "",
        )

        val jvmEmptyModuleGraph = runBlocking {
            doTest(
                aom,
                resolutionInput = ResolutionInput(
                    DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "jvm-empty",
                expected = """
                    module:jvm-empty
                    ├─── jvm-empty:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    │         ╰─── org.jetbrains:annotations:13.0
                    ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
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
                    ├─── jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    ├─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    ╰─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                         ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion} (*)
                    """.trimIndent()
            )
        }

        runBlocking {
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-stdlib",
                graph = jvmEmptyModuleGraph,
                expected = """
                    module:jvm-empty
                    ├─── jvm-empty:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}
                    │         ╰─── org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
                    │              ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    ├─── jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    ├─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    ╰─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                         ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion} (*)
                """.trimIndent()
            )
            assertInsight(
                group = "org.opentest4j",
                module = "opentest4j",
                graph = jvmEmptyModuleGraph,
                expected = """
                    module:jvm-empty
                    ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}
                    │         ╰─── org.junit.jupiter:junit-jupiter-api:5.10.1
                    │              ╰─── org.opentest4j:opentest4j:1.3.0
                    ╰─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                         ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion} (*)
                """.trimIndent()
            )
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-test-junit5",
                graph = jvmEmptyModuleGraph,
                expected = """
                    module:jvm-empty
                    ├─── jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                    │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}
                    ╰─── jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}, implicit
                         ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.kotlinVersion}
                """.trimIndent()
            )
            assertInsight(
                group = "org.jetbrains.kotlin", module = "XXX", graph = jvmEmptyModuleGraph,
                expected = "module:jvm-empty"
            )
            assertInsight(
                group = "XXX", module = "kotlin-test-junit", graph = jvmEmptyModuleGraph,
                expected = "module:jvm-empty"
            )
        }
    }

    @Test
    fun `test compose-multiplatform - shared compile dependencies insights`(testInfo: TestInfo) {
        val aom = getTestProjectModel("compose-multiplatform", testDataRoot)

        val sharedModuleIosArm64Graph = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = setOf(ResolutionPlatform.IOS_ARM64),
                        isTest = false,
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "shared",
            )
        }

        // Subgraph for "org.jetbrains.kotlin:kotlin-stdlib" shows places referencing the dependency
        // of the exact effective version only (${UsedVersions.kotlinVersion}).
        // There are other paths to this dependency referencing another version of this dependency, those are skipped as well as same-version constraints.
        runBlocking {
            assertInsightByFile(
                group = "org.jetbrains.compose.ui",
                module = "ui-graphics",
                graph = sharedModuleIosArm64Graph,
                insightFile = "ui-graphics"
            )

            // Subgraph for "org.jetbrains.kotlin:kotlin-stdlib-common" shows all places referencing the dependency
            // since none of those places references the exact effective version (${UsedVersions.kotlinVersion}).
            // Also, the path to the constraint defining the effective version (${UsedVersions.kotlinVersion}) is also presented in a graph.
            assertInsightByFile(
                group = "org.jetbrains.kotlin",
                module = "kotlin-stdlib-common",
                graph = sharedModuleIosArm64Graph,
                insightFile = "kotlin-stdlib-common"
            )

            // Assert that all dependencies "org.jetbrains.kotlin:kotlin-stdlib-common" have correct overriddenBy
            sharedModuleIosArm64Graph
                .distinctBfsSequence()
                .filterIsInstance<MavenDependencyNode>()
                .filter { it.group == "org.jetbrains.kotlin" && it.module == "kotlin-stdlib-common" }
                .forEach {
                    if (it.originalVersion() == it.resolvedVersion()) {
                        assertNull(it.overriddenBy)
                    } else {
                        assertNotNull(
                            it.overriddenBy,
                            "Expected non-null 'overriddenBy' since ${it.resolvedVersion()} doesn't match ${it.originalVersion()}"
                        )
                        val constraintNode = it.overriddenBy.singleOrNull() as? MavenDependencyConstraintNode
                        assertNotNull(
                            constraintNode,
                            "Expected the only dependency constraint node in 'overriddenBy', but found ${
                                it.overriddenBy.map { it.key }.toSet()
                            }"
                        )
                        assertEquals(
                            constraintNode.key.name, "org.jetbrains.kotlin:kotlin-stdlib-common",
                            "Unexpected constraint ${constraintNode.key}"
                        )
                    }
                }

            assertInsightByFile(
                group = "org.jetbrains.kotlinx",
                module = "kotlinx-coroutines-core",
                graph = sharedModuleIosArm64Graph,
                insightFile = "kotlinx-coroutines-core"
            )
        }
    }

    /**
     * Test checks that if node has child that caused version overriding,
     * then other children of this node are not included in the insight graph
     * built for a resolved version diagnostic.
     */
    @Test
    fun `test jvm-dependency-insights - A`(testInfo: TestInfo) {
        val aom = getTestProjectModel("jvm-dependency-insights", testDataRoot)

        val aGraph = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = setOf(ResolutionPlatform.JVM),
                        isTest = false,
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "A",
            )
        }

        // Subgraph for "org.jetbrains.kotlinx:kotlin-coroutine-core" shows places referencing the dependency
        // of the exact effective version only (${UsedVersions.kotlinVersion}).
        // There are other paths to this dependency referencing another version of this dependency, those are skipped as well as same-version constraints.
        runBlocking {
            assertInsightByFile(
                group = "org.jetbrains.kotlinx",
                module = "kotlinx-coroutines-core",
                graph = aGraph,
                insightFile = "jvm-dependency-insights-A-kotlinx-coroutines-core"
            )

            // Assert that all dependencies "org.jetbrains.kotlin:kotlinx-coroutines-core" have a correct overriddenBy
            aGraph
                .distinctBfsSequence()
                .filterIsInstance<MavenDependencyNode>()
                .filter { it.group == "org.jetbrains.kotlinx" && it.module == "kotlinx-coroutines-core" }
                .forEach {
                    if (it.originalVersion() == it.resolvedVersion()) {
                        assertNull(it.overriddenBy.takeIf { it.isNotEmpty() })
                    } else {
                        assertNotNull(
                            it.overriddenBy,
                            "Expected non-null 'overriddenBy' since ${it.resolvedVersion()} doesn't match ${it.originalVersion()}"
                        )
                        val dependencyNode = it.overriddenBy.filterIsInstance<MavenDependencyNode>().singleOrNull()
                        assertNotNull(
                            dependencyNode,
                            "Expected the only dependency node in 'overriddenBy', but found ${
                                it.overriddenBy.map { it.key }.toSet()
                            }"
                        )
                        assertEquals(
                            dependencyNode.key.name, "org.jetbrains.kotlinx:kotlinx-coroutines-core",
                            "Unexpected dependency node ${dependencyNode.key}"
                        )
                        val constraintNode = it.overriddenBy.filterIsInstance<MavenDependencyConstraintNode>().singleOrNull()
                        assertNotNull(
                            constraintNode,
                            "Expected the only constraint node in 'overriddenBy', but found ${
                                it.overriddenBy.map { it.key }.toSet()
                            }"
                        )
                        assertEquals(
                            constraintNode.key.name, "org.jetbrains.kotlinx:kotlinx-coroutines-core",
                            "Unexpected constraint node ${constraintNode.key}"
                        )
                    }
                }
        }
    }

    @Test
    fun `test jvm-dependency-insights - B`(testInfo: TestInfo) {
        val aom = getTestProjectModel("jvm-dependency-insights", testDataRoot)

        val bGraph = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = setOf(ResolutionPlatform.JVM),
                        isTest = false,
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "B",
            )
        }

        // Subgraph for "org.jetbrains.kotlinx:kotlin-coroutine-core" shows places referencing the dependency
        // of the exact effective version only (${UsedVersions.kotlinVersion}).
        // There are other paths to this dependency referencing another version of this dependency, those are skipped as well as same-version constraints.
        runBlocking {
            assertInsightByFile(
                group = "org.jetbrains.compose.runtime",
                module = "runtime",
                graph = bGraph,
                insightFile = "jvm-dependency-insights-B-compose-runtime"
            )
        }
    }

    @Test
    fun `test jvm-dependency-insights - C`(testInfo: TestInfo) {
        val aom = getTestProjectModel("jvm-dependency-insights", testDataRoot)

        val cGraph = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = setOf(ResolutionPlatform.JVM),
                        isTest = false,
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "C",
            )
        }

        runBlocking {
            assertInsightByFile(
                group = "org.jetbrains.kotlinx",
                module = "kotlinx-coroutines-test",
                graph = cGraph,
                insightFile = "jvm-dependency-insights-C-kotlinx-coroutines-test"
            )
        }
    }

    @Test
    fun `test jvm-dependency-insights - D`(testInfo: TestInfo) {
        val aom = getTestProjectModel("jvm-dependency-insights", testDataRoot)

        val dGraph = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = setOf(ResolutionPlatform.JVM),
                        isTest = false,
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "D",
            )
        }

        runBlocking {
            assertInsightByFile(
                group = "org.jetbrains.kotlinx",
                module = "kotlinx-coroutines-test",
                graph = dGraph,
                insightFile = "jvm-dependency-insights-D-kotlinx-coroutines-test"
            )
        }
    }

    @Test
    fun `test jvm-dependency-insights - E`(testInfo: TestInfo) {
        val aom = getTestProjectModel("jvm-dependency-insights", testDataRoot)

        val eGraph = runBlocking {
            doTestByFile(
                testInfo,
                aom,
                ResolutionInput(
                    DependenciesFlowType.ClassPathType(
                        scope = ResolutionScope.COMPILE,
                        platforms = setOf(ResolutionPlatform.JVM),
                        isTest = false,
                    ),
                    ResolutionDepth.GRAPH_FULL,
                    fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot),
                ),
                module = "E",
            )
        }

        runBlocking {
            assertInsightByFile(
                group = "org.junit.jupiter",
                module = "junit-jupiter-api",
                graph = eGraph,
                insightFile = "jvm-dependency-insights-E-junit-jupiter-api"
            )
        }
    }

    private fun assertInsightByFile(group: String, module: String, graph: DependencyNode, insightFile: String) {
        val expectedResolved = getGoldenFileText("$insightFile.insight.resolved.txt", fileDescription = "Golden file with insight for resolved version only")
        withActualDump(expectedResultPath = testGoldenFilesRoot.resolve("$insightFile.insight.resolved.txt")) {
            assertInsight(group, module, graph, expectedResolved, resolvedVersionOnly = true)
        }

        val expectedFull = getGoldenFileText("$insightFile.insight.full.txt", fileDescription = "Golden file with full insight")
        withActualDump(expectedResultPath = testGoldenFilesRoot.resolve("$insightFile.insight.full.txt")) {
            assertInsight(group, module, graph, expectedFull, resolvedVersionOnly = false)
        }
    }

    private fun assertInsight(group: String, module: String, graph: DependencyNode, expected: String, resolvedVersionOnly: Boolean = false) {
        with(moduleDependenciesResolver) {
            val subGraph = dependencyInsight(group, module, graph, resolvedVersionOnly)
            assertEquals(expected, subGraph, MavenCoordinates(group, module, null))
        }
    }
}
