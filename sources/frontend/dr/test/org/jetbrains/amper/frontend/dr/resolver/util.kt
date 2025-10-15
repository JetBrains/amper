/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.readProjectModel
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.problems.reporting.NoopProblemReporter
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.test.fail
import kotlin.time.Duration.Companion.minutes

/**
 * [runTest] but with a 5-minute timeout.
 */
internal fun runSlowTest(testBody: suspend TestScope.() -> Unit) = runTest(timeout = 5.minutes, testBody = testBody)

internal fun DependencyNode.moduleDeps(name: String): ModuleDependencyNodeWithModule = children
    .filterIsInstance<ModuleDependencyNodeWithModule>()
    .single { it.module.userReadableName == name }

internal fun DependencyNode.fragmentDeps(module: String, fragment: String) =
    moduleDeps(module).children
        .filterIsInstance<DirectFragmentDependencyNodeHolder>()
        .filter { it.fragment.name == fragment }

internal fun getTestProjectModel(testProjectName: String, testDataRoot: Path): Model {
    val projectPath = testDataRoot.resolve(testProjectName)
    val aom = with(NoopProblemReporter) {
        val amperProjectContext = StandaloneAmperProjectContext.create(projectPath, null)
            ?: fail("Failed to create test project context")
        amperProjectContext.readProjectModel(pluginData = emptyList(), mavenPluginXmls = emptyList())
            ?: fail("Failed to read Amper's project model")
    }
    return aom
}

internal val amperUserCacheRoot: AmperUserCacheRoot
    get() {
        val result = AmperUserCacheRoot.fromCurrentUserResult()
        assertIs<AmperUserCacheRoot>(result)
        return result
    }
