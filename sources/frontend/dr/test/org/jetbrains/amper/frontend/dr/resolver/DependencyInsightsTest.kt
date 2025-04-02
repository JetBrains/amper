/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.DependencyNode
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
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.fail

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
                    +--- jvm-empty:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    |         \--- org.jetbrains:annotations:13.0
                    +--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    +--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
                    |         +--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
                    |         |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    |         \--- junit:junit:4.13.2
                    |              \--- org.hamcrest:hamcrest-core:1.3
                    +--- jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    +--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion} (*)
                    \--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                         \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)
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
                    +--- jvm-empty:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    +--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    +--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
                    |         \--- org.jetbrains.kotlin:kotlin-test:${UsedVersions.kotlinVersion}
                    |              \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    +--- jvm-empty:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    +--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.kotlinVersion}
                    \--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                         \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)
                """.trimIndent()
            )
            assertInsight(
                group = "org.hamcrest",
                module = "hamcrest-core",
                graph = jvmEmptyModuleGraph,
                expected = """
                    module:jvm-empty
                    +--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
                    |         \--- junit:junit:4.13.2
                    |              \--- org.hamcrest:hamcrest-core:1.3
                    \--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                         \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion} (*)
                """.trimIndent()
            )
            assertInsight(
                group = "org.jetbrains.kotlin",
                module = "kotlin-test-junit",
                graph = jvmEmptyModuleGraph,
                expected = """
                    module:jvm-empty
                    +--- jvm-empty:commonTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                    |    \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
                    \--- jvm-empty:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}, implicit
                         \--- org.jetbrains.kotlin:kotlin-test-junit:${UsedVersions.kotlinVersion}
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

    private fun assertInsightByFile(group: String, module: String, graph: DependencyNode, insightFile: String) {
        val expected = getGoldenFileText("$insightFile.insight.txt", fileDescription = "Golden file with insight")
        assertInsight(group, module, graph, expected)
    }

    private fun assertInsight(group: String, module: String, graph: DependencyNode, expected: String) {
        with(moduleDependenciesResolver) {
            val subGraph = dependencyInsight(group, module, graph)
            assertEquals(expected, subGraph)
        }
    }
}
