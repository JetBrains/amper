/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.core.messages.NoOpCollectingProblemReporter
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.dependency.resolution.MavenDependencyNode
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.collectBuildProblems
import org.jetbrains.amper.frontend.dr.resolver.diagnostics.reporters.DependencyBuildProblem
import org.jetbrains.amper.test.Dirs
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.contracts.ExperimentalContracts
import kotlin.test.assertTrue

class DiagnosticsTest: BaseModuleDrTest() {

    @Test
    fun `test sync diagnostics`() {
        val aom = getTestProjectModel("multi-module-failed-resolve", testDataRoot)

        kotlin.test.assertEquals(
            setOf("common", "commonTest", "jvm", "jvmTest"),
            aom.modules.single{it.userReadableName == "shared"}.fragments.map { it.name }.toSet(),
            ""
        )

        val sharedTestFragmentDeps = runBlocking {
            doTest(
                aom,
                ResolutionInput(DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL) ,
                module = "shared",
                expected = """module:shared
                    |+--- shared:common:org.jetbrains.compose.foundation:foundation:12.12.12
                    ||    \--- org.jetbrains.compose.foundation:foundation:12.12.12
                    |+--- shared:common:org.jetbrains.compose.material3:material3:12.12.12
                    ||    \--- org.jetbrains.compose.material3:material3:12.12.12
                    |+--- shared:common:org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                    ||    \--- org.jetbrains.kotlinx:kotlinx-serialization-core:13.13.13
                    |+--- shared:common:org.jetbrains.kotlin:kotlin-stdlib:2.1.10, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.10
                    ||         \--- org.jetbrains:annotations:13.0
                    |+--- shared:common:org.jetbrains.compose.runtime:runtime:12.12.12
                    ||    \--- org.jetbrains.compose.runtime:runtime:12.12.12
                    |+--- shared:commonTest:org.jetbrains.kotlin:kotlin-stdlib:2.1.10, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.10 (*)
                    |+--- shared:commonTest:org.jetbrains.kotlin:kotlin-test-junit:2.1.10, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-test-junit:2.1.10
                    ||         +--- org.jetbrains.kotlin:kotlin-test:2.1.10
                    ||         |    \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.10 (*)
                    ||         \--- junit:junit:4.13.2
                    ||              \--- org.hamcrest:hamcrest-core:1.3
                    |+--- shared:commonTest:org.jetbrains.compose.runtime:runtime:12.12.12
                    ||    \--- org.jetbrains.compose.runtime:runtime:12.12.12
                    |+--- shared:jvm:org.jetbrains.kotlin:kotlin-stdlib:2.1.10, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.10 (*)
                    |+--- shared:jvm:org.jetbrains.compose.runtime:runtime:12.12.12
                    ||    \--- org.jetbrains.compose.runtime:runtime:12.12.12
                    |+--- shared:jvmTest:org.jetbrains.kotlin:kotlin-stdlib:2.1.10, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-stdlib:2.1.10 (*)
                    |+--- shared:jvmTest:org.jetbrains.kotlin:kotlin-test-junit:2.1.10, implicit
                    ||    \--- org.jetbrains.kotlin:kotlin-test-junit:2.1.10 (*)
                    |\--- shared:jvmTest:org.jetbrains.compose.runtime:runtime:12.12.12
                    |     \--- org.jetbrains.compose.runtime:runtime:12.12.12""".trimMargin(),
                messagesCheck = { node ->
                    if (!assertDependencyError(node, "org.jetbrains.compose.foundation", "foundation")
                        && !assertDependencyError(node, "org.jetbrains.compose.material3", "material3")
                        && !assertDependencyError(node, "org.jetbrains.compose.runtime", "runtime")
                        && !assertDependencyError(node, "org.jetbrains.kotlinx", "kotlinx-serialization-core"))
                    {
                        val messages = node.messages.defaultFilterMessages()
                        assertTrue(messages.isEmpty(), "There must be no messages for $this: $messages")
                    }
                }
            )
        }

        runBlocking {
            downloadAndAssertFiles(
                files = """
                |annotations-13.0.jar
                |hamcrest-core-1.3.jar
                |junit-4.13.2.jar
                |kotlin-stdlib-2.1.10.jar
                |kotlin-test-2.1.10.jar
                |kotlin-test-junit-2.1.10.jar
                """.trimMargin(),
                sharedTestFragmentDeps
            )
        }

        val diagnosticsReporter = NoOpCollectingProblemReporter()
        collectBuildProblems(sharedTestFragmentDeps, diagnosticsReporter, Level.Error)
        val buildProblems = diagnosticsReporter.getProblems()

        kotlin.test.assertEquals(7, buildProblems.size)

        // direct dependency on a built-in library,
        // a version of the library is taken from settings:compose:version in file module.yaml
        checkDependencyBuildProblem(buildProblems, "org.jetbrains.compose.foundation", "foundation",
            "module.yaml",16)
        checkDependencyBuildProblem(buildProblems, "org.jetbrains.compose.material3", "material3",
            "module.yaml",16)

        // transitive dependency of built-in libraries,
        // a version of the library is taken from settings:compose:version in file module.yaml
        checkDependencyBuildProblem(buildProblems, "org.jetbrains.compose.runtime", "runtime",
            "module.yaml",16)

        // transitive dependency on a built-in library,
        // a version of the library is taken from settings:serialization:version in file template.yaml
        checkDependencyBuildProblem(buildProblems, "org.jetbrains.kotlinx", "kotlinx-serialization-core",
            "..\\..\\templates\\template.yaml"
                .let{ if (DefaultSystemInfo.detect().family != OsFamily.Windows) it.replace("\\", "/") else it },
            5)
    }

    // todo (AB) : Test the case where version is not resolved and it is default one (compose.enabled is set only)
    // todo (AB) : (due to the network failure for instance or has a strict conflict with another dependency)
    // todo (AB) : What is reported in this case?

    @OptIn(ExperimentalContracts::class)
    internal fun DependencyNode.isMavenDependency(group: String, module: String): Boolean {
        return this is MavenDependencyNode && this.group == group && this.module == module
    }

    private fun assertDependencyError(node: DependencyNode, group: String, module: String) : Boolean{
        if (node.isMavenDependency(group, module)) {
            node as MavenDependencyNode
            kotlin.test.assertEquals(
                setOf("Unable to resolve dependency ${node.dependency.group}:${node.dependency.module}:${node.dependency.version}"),
                node.messages.map{ it.text }.toSet()
            )
            return true
        }
        return false
    }

    private fun checkDependencyBuildProblem(buildProblems: Collection<BuildProblem>, group: String,
                                            module: String, filePath: String, versionLineNumber: Int?) {
        val relevantBuildProblems = buildProblems.filter {
            it is DependencyBuildProblem
                    && it.problematicDependency.isMavenDependency(group, module) }

        relevantBuildProblems.forEach { buildProblem ->
            buildProblem as DependencyBuildProblem
            val mavenDependency = (buildProblem.problematicDependency as MavenDependencyNode).dependency

            kotlin.test.assertEquals(
                setOf("Unable to resolve dependency ${mavenDependency.group}:${mavenDependency.module}:${mavenDependency.version}" +
                        (if (versionLineNumber != null) ". The version ${mavenDependency.version} is defined at $filePath:$versionLineNumber" else "")),
                setOf(buildProblem.errorMessage.text)
            )
        }
    }
}