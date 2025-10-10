/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.dependency.resolution.diagnostics.detailedMessage
import org.jetbrains.amper.dependency.resolution.orUnspecified
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.collectBuildProblems
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.DependencyBuildProblem
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.ModuleDependencyWithOverriddenVersion
import org.jetbrains.amper.problems.reporting.BuildProblem
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.problems.reporting.Level
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInfo
import org.junit.jupiter.api.fail
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract
import kotlin.io.path.Path
import kotlin.io.path.div
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class DiagnosticsTest : BaseModuleDrTest() {

    override val testGoldenFilesRoot: Path
        get() = super.testGoldenFilesRoot.resolve("diagnostics")

    @Test
    fun `test sync diagnostics`() = runTest {
        val aom = getTestProjectModel("multi-module-failed-resolve", testDataRoot)

        assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules.single { it.userReadableName == "shared" }.fragments.map { it.name }.toSet(),
            ""
        )

        val sharedTestFragmentDeps = doTest(
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
                ├─── shared:common:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}
                │         ╰─── org.jetbrains:annotations:13.0
                ├─── shared:common:org.jetbrains.compose.runtime:runtime:12.12.12, implicit (because Compose is enabled)
                │    ╰─── org.jetbrains.compose.runtime:runtime:12.12.12
                ├─── shared:commonTest:org.jetbrains.compose.foundation:foundation:12.12.12
                │    ╰─── org.jetbrains.compose.foundation:foundation:12.12.12
                ├─── shared:commonTest:org.jetbrains.compose.material3:material3:12.12.12
                │    ╰─── org.jetbrains.compose.material3:material3:12.12.12
                ├─── shared:commonTest:org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                │    ╰─── org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                ├─── shared:commonTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion} (*)
                ├─── shared:commonTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.defaultKotlinVersion}, implicit (because the test engine is junit-5)
                │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.defaultKotlinVersion}
                │         ├─── org.jetbrains.kotlin:kotlin-test:${UsedVersions.defaultKotlinVersion}
                │         │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion} (*)
                │         ╰─── org.junit.jupiter:junit-jupiter-api:5.10.1
                │              ├─── org.junit:junit-bom:5.10.1
                │              ├─── org.opentest4j:opentest4j:1.3.0
                │              ├─── org.junit.platform:junit-platform-commons:1.10.1
                │              │    ├─── org.junit:junit-bom:5.10.1
                │              │    ╰─── org.apiguardian:apiguardian-api:1.1.2
                │              ╰─── org.apiguardian:apiguardian-api:1.1.2
                ├─── shared:commonTest:org.jetbrains.compose.runtime:runtime:12.12.12, implicit (because Compose is enabled)
                │    ╰─── org.jetbrains.compose.runtime:runtime:12.12.12
                ├─── shared:jvm:org.jetbrains.compose.foundation:foundation:12.12.12
                │    ╰─── org.jetbrains.compose.foundation:foundation:12.12.12
                ├─── shared:jvm:org.jetbrains.compose.material3:material3:12.12.12
                │    ╰─── org.jetbrains.compose.material3:material3:12.12.12
                ├─── shared:jvm:org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                │    ╰─── org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                ├─── shared:jvm:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion} (*)
                ├─── shared:jvm:org.jetbrains.compose.runtime:runtime:12.12.12, implicit (because Compose is enabled)
                │    ╰─── org.jetbrains.compose.runtime:runtime:12.12.12
                ├─── shared:jvmTest:org.jetbrains.compose.foundation:foundation:12.12.12
                │    ╰─── org.jetbrains.compose.foundation:foundation:12.12.12
                ├─── shared:jvmTest:org.jetbrains.compose.material3:material3:12.12.12
                │    ╰─── org.jetbrains.compose.material3:material3:12.12.12
                ├─── shared:jvmTest:org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                │    ╰─── org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                ├─── shared:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}, implicit
                │    ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion} (*)
                ├─── shared:jvmTest:org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.defaultKotlinVersion}, implicit (because the test engine is junit-5)
                │    ╰─── org.jetbrains.kotlin:kotlin-test-junit5:${UsedVersions.defaultKotlinVersion} (*)
                ╰─── shared:jvmTest:org.jetbrains.compose.runtime:runtime:12.12.12, implicit (because Compose is enabled)
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

        downloadAndAssertFiles(
            listOf(
                "annotations-13.0.jar",
                "apiguardian-api-1.1.2.jar",
                "junit-jupiter-api-5.10.1.jar",
                "junit-platform-commons-1.10.1.jar",
                "kotlin-stdlib-${UsedVersions.defaultKotlinVersion}.jar",
                "kotlin-test-${UsedVersions.defaultKotlinVersion}.jar",
                "kotlin-test-junit5-${UsedVersions.defaultKotlinVersion}.jar",
                "opentest4j-1.3.0.jar",
            ),
            sharedTestFragmentDeps
        )

        val diagnosticsReporter = CollectingProblemReporter()
        collectBuildProblems(sharedTestFragmentDeps, diagnosticsReporter, Level.Error)
        val buildProblems = diagnosticsReporter.problems

        /**
         * This magic number appears because we are diagnosing each fragment (4 fragments total),
         * and each fragment contains 4 incorrect dependencies.
         * 
         * The common fragment contains incorrect dependencies by definition in module file.
         * More specific fragments contain incorrect dependencies because they were propagated during merge.
         */
        assertEquals(16, buildProblems.size)

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
            Path("..") / "templates" / "template.yaml",
            5, 7
        )
    }

    // AMPER-4270
    @Test
    fun `overridden version for BOM version is not displayed for unspecified versions`() = runTest {
        val aom = getTestProjectModel("jvm-bom-support", testDataRoot)
        val mainFragmentDeps = doTest(
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot)
            ),
            module = "app",
            fragment = "main",
            expected = """
                Fragment 'app.main' dependencies
                ├─── app:main:com.fasterxml.jackson.core:jackson-annotations:unspecified
                │    ╰─── com.fasterxml.jackson.core:jackson-annotations:unspecified -> 2.18.3
                │         ╰─── com.fasterxml.jackson:jackson-bom:2.18.3
                │              ╰─── com.fasterxml.jackson.core:jackson-annotations:2.18.3 (c)
                ├─── app:main:com.fasterxml.jackson:jackson-bom:2.18.3
                │    ╰─── com.fasterxml.jackson:jackson-bom:2.18.3 (*)
                ╰─── app:main:org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}, implicit
                     ╰─── org.jetbrains.kotlin:kotlin-stdlib:${UsedVersions.defaultKotlinVersion}
                          ╰─── org.jetbrains:annotations:13.0
            """.trimIndent(),
            verifyMessages = false,
        )

        val diagnosticsReporter = CollectingProblemReporter()
        collectBuildProblems(mainFragmentDeps, diagnosticsReporter, Level.Warning)
        val buildProblems = diagnosticsReporter.problems

        assertEquals(
            0, buildProblems.size,
            "No problems should be reported for JVM deps, but got the following ${buildProblems.size} problem(s):\n${
                buildProblems.joinToString(
                    "\n"
                ) { it.message }
            }"
        )
    }

    /**
     * This test checks that WARNING is reported in case a direct dependency version was unspecified and
     * taken from BOM, but later was overridden due to conflict resolution.
     */
    @Test
    fun `overridden version for unspecified version resolved from BOM is detected`(testInfo: TestInfo) = runTest {
        val aom = getTestProjectModel("jvm-bom-support-overridden", testDataRoot)
        val commonDeps = doTestByFile(
            testInfo = testInfo,
            aom,
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot)
            ),
            module = "app",
            fragment = "main",
            verifyMessages = false,
        )

        val diagnosticsReporter = CollectingProblemReporter()
        collectBuildProblems(commonDeps, diagnosticsReporter, Level.Warning)

        val buildProblem = diagnosticsReporter.problems.singleOrNull()

        assertNotNull (
            buildProblem,
            "One build problem should be reported for dependency 'io.ktor:ktor-client-cio-jvm', " +
                    "but got the following ${diagnosticsReporter.problems.size} problem(s):\n${
                        diagnosticsReporter.problems.joinToString("\n") { it.message }
                    }"
        )

        buildProblem as ModuleDependencyWithOverriddenVersion

        assertEquals(buildProblem.dependencyNode.key.name, "io.ktor:ktor-client-cio-jvm",
            "Build problem is reported for unexpected dependency")
        assertEquals(buildProblem.level, Level.Warning, "Unexpected build problem level")

        assertNull(buildProblem.dependencyNode.version, "Original version should be left unspecified")
        assertEquals(buildProblem.dependencyNode.versionFromBom, "3.0.2", "Incorrect version resolved from BOM")
        assertEquals(buildProblem.message,
            "Version 3.0.2 of dependency io.ktor:ktor-client-cio-jvm taken from BOM is overridden, the actual version is 3.1.2.",
            "Unexpected diagnostic message"
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

            assertContains(
                buildProblem.message,
                "Unable to resolve dependency ${mavenDependency.group}:${mavenDependency.module}:${mavenDependency.version.orUnspecified()}"
            )
            if (versionLineNumber != null) {
                assertContains(
                    buildProblem.message,
                    "The version ${mavenDependency.version} is defined at $filePath:$versionLineNumber:$versionColumn"
                )
            } else {
                assertFalse(buildProblem.message.contains("The version ${mavenDependency.version} is defined at"))
            }
        }
    }
}
