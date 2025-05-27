/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.helpers


import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.types.ATypes
import org.jetbrains.amper.frontend.old.helper.TestBase
import java.nio.file.Path


/**
 * Test that the passed module file is read correctly.
 */
fun TestBase.testModuleRead(
    caseName: String,
    types: ATypes = TestATypesDiscoverer(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath,
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readModule,
).doTest()

/**
 * Tests that the passed module file is read and refined correctly.
 */
fun TestBase.testRefineModule(
    caseName: String,
    selectedContexts: Contexts,
    types: ATypes = TestATypesDiscoverer(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath,
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readAndRefineModule(selectedContexts),
).doTest()

/**
 * Tests that the passed module file with templates is read and refined correctly.
 */
fun TestBase.testRefineModuleWithTemplates(
    caseName: String,
    selectedContexts: (Path) -> Contexts,
    types: ATypes = TestATypesDiscoverer(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath,
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readAndRefineModuleWithTemplates(selectedContexts),
    dumpPathContexts = true,
).doTest()

/**
 * Tests that the diagnostics created during module read are the same as in the file.
 */
fun TestBase.diagnoseModuleRead(
    caseName: String,
    types: ATypes = TestATypesDiscoverer(),
) = DiagnosticsTreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath,
    types = types,
    treeBuilder = readModule,
).doTest()