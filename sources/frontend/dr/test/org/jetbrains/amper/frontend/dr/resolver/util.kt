/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.dr.resolver

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.get
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.dependency.resolution.DependencyNode
import org.jetbrains.amper.frontend.Model
import org.jetbrains.amper.frontend.aomBuilder.SchemaBasedModelImport
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import java.nio.file.Path
import kotlin.test.assertIs
import kotlin.test.fail

internal fun DependencyNode.moduleDeps(name: String): ModuleDependencyNodeWithModule = children
    .filterIsInstance<ModuleDependencyNodeWithModule>()
    .single { it.module.userReadableName == name }

internal fun DependencyNode.fragmentDeps(module: String, fragment: String) =
    moduleDeps(module).children
        .filterIsInstance<DirectFragmentDependencyNodeHolder>()
        .filter { it.fragment.name == fragment }

internal fun getTestProjectModel(testProjectName: String, testDataRoot: Path): Model {
    val projectPath = testDataRoot.resolve(testProjectName)
    val aom = with(object : ProblemReporterContext {
        override val problemReporter: ProblemReporter = TestProblemReporter()
    }) {
        val amperProjectContext =
            StandaloneAmperProjectContext.create(projectPath, null) ?: fail("Fails to create test project context")
        SchemaBasedModelImport.getModel(amperProjectContext).get()
    }
    return aom
}

internal val amperUserCacheRoot: AmperUserCacheRoot
    get() {
        val result = AmperUserCacheRoot.fromCurrentUserResult()
        assertIs<AmperUserCacheRoot>(result)
        return result
    }
