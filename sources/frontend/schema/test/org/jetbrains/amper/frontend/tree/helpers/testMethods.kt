/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.helpers


import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.test.golden.GoldenTest


/**
 * Test that the passed module file is read correctly.
 */
fun GoldenTest.testModuleRead(
    caseName: String,
    types: SchemaTypingContext = TestSchemaTypingContext(),
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
    types: SchemaTypingContext = TestSchemaTypingContext(),
    expectPostfix: String = ".result.json",
    withDefaults: Boolean = false,
) = TreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath(),
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readAndRefineModule(selectedContexts, withDefaults),
).doTest()

/**
 * Tests that the passed module file with templates is read and refined correctly.
 */
fun GoldenTest.testRefineModuleWithTemplates(
    caseName: String,
    selectedContexts: (VirtualFile) -> Contexts,
    types: SchemaTypingContext = TestSchemaTypingContext(),
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
    types: SchemaTypingContext = TestSchemaTypingContext(),
) = DiagnosticsTreeTestRun(
    caseName = caseName,
    base = baseTestResourcesPath(),
    types = types,
    treeBuilder = readModule,
).doTest()