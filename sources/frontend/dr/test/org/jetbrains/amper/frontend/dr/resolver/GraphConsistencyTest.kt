/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.core.messages.BuildProblem
import org.jetbrains.amper.core.messages.CollectingProblemReporter
import org.jetbrains.amper.core.messages.Level
import org.jetbrains.amper.dependency.resolution.ResolutionPlatform
import org.jetbrains.amper.dependency.resolution.ResolutionScope
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.test.Dirs
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

class GraphConsistencyTest {

    private val testDataRoot: Path = Dirs.amperSourcesRoot.resolve("frontend/dr/testData/projects")

    @Test
    fun `check parents in a dependencies graph - ide`() {
        val aom = getTestProjectModel("jvm-transitive-dependencies", testDataRoot)
        checkParentsInDependenciesGraph(
            ResolutionInput(
                DependenciesFlowType.IdeSyncType(aom), ResolutionDepth.GRAPH_FULL,
                fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot)
            ),
            aom
        )
    }

    @Test
    fun `check parents in a dependencies graph - classpath`() = checkParentsInDependenciesGraph(
        ResolutionInput(
            DependenciesFlowType.ClassPathType(ResolutionScope.RUNTIME, setOf(ResolutionPlatform.JVM), isTest = false),
            ResolutionDepth.GRAPH_FULL,
            fileCacheBuilder = getAmperFileCacheBuilder(amperUserCacheRoot)
        )
    )

    private fun checkParentsInDependenciesGraph(
        resolutionInput: ResolutionInput,
        aom: Model = getTestProjectModel("jvm-transitive-dependencies", testDataRoot)
    ) {
        val graph = runBlocking {
            with(moduleDependenciesResolver) {
                aom.modules.resolveDependencies(resolutionInput)
            }
        }

        graph.distinctBfsSequence().forEach {
            val parents = it.parents
            assertTrue("Parents are empty for node ${it.key}") { parents.isNotEmpty() || graph == it }

            it.parents.forEach { parent ->
                assertTrue("Node ${parent.key} is registered as parent of node ${it.key}, but doesn't contain it among its children") {
                    parent.children.contains(it)
                }
            }
        }
    }
}

class TestProblemReporter : CollectingProblemReporter() {
    override fun doReportMessage(message: BuildProblem) {}

    fun clearAll() = problems.clear()

    fun getDiagnostics(vararg levels: Level = arrayOf(Level.Error, Level.Fatal)): List<BuildProblem> =
        problems.filter { levels.contains(it.level) }
}
