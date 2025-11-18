/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.problems.reporting.NoopProblemReporter
import org.jetbrains.amper.test.Dirs
import java.nio.file.Path
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * [runTest] but with a 5-minute timeout.
 */
internal fun runSlowTest(context: CoroutineContext = EmptyCoroutineContext, testBody: suspend TestScope.() -> Unit) =
    runTest(context = context, timeout = 5.minutes, testBody = { withContext(Dispatchers.Default) { testBody() }})

internal fun DependencyNode.moduleDeps(name: String): ModuleDependencyNode = children
    .filterIsInstance<ModuleDependencyNode>()
    .single { it.moduleName == name }

internal fun DependencyNode.fragmentDeps(module: String, fragment: String) =
    moduleDeps(module).children
        .filterIsInstance<DirectFragmentDependencyNode>()
        .filter { it.fragmentName == fragment }

internal fun getTestProjectModel(testProjectName: String, testDataRoot: Path): Model {
    val projectPath = testDataRoot.resolve(testProjectName)
    val aom = with(NoopProblemReporter) {
        val amperProjectContext = StandaloneAmperProjectContext.create(projectPath, null)
            ?: fail("Failed to create test project context")
        amperProjectContext.readProjectModel(pluginData = emptyList(), mavenPluginXmls = emptyList())
    }
    return aom
}

internal val amperUserCacheRoot: AmperUserCacheRoot
    get() {
        val result = AmperUserCacheRoot(Dirs.userCacheRoot)
        assertIs<AmperUserCacheRoot>(result)
        return result
    }
