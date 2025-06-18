/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.helpers


import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.types.AmperTypes
import org.jetbrains.amper.test.golden.GoldenTest
import java.nio.file.Path


/**
 * Test that the passed module file is read correctly.
 */
fun GoldenTest.testModuleRead(
    caseName: String,
    types: AmperTypes = TestAmperTypesDiscoverer(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath(),
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readModule,
).doTest()

/**
 * Tests that the passed module file is read and refined correctly.
 */
fun GoldenTest.testRefineModule(
    caseName: String,
    selectedContexts: Contexts,
    types: AmperTypes = TestAmperTypesDiscoverer(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath(),
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readAndRefineModule(selectedContexts),
).doTest()

/**
 * Tests that the passed module file with templates is read and refined correctly.
 */
fun GoldenTest.testRefineModuleWithTemplates(
    caseName: String,
    selectedContexts: (Path) -> Contexts,
    types: AmperTypes = TestAmperTypesDiscoverer(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath(),
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readAndRefineModuleWithTemplates(selectedContexts),
    dumpPathContexts = true,
).doTest()

/**
 * Tests that the diagnostics created during module read are the same as in the file.
 */
fun GoldenTest.diagnoseModuleRead(
    caseName: String,
    types: AmperTypes = TestAmperTypesDiscoverer(),
) = DiagnosticsTreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath(),
    types = types,
    treeBuilder = readModule,
).doTest()