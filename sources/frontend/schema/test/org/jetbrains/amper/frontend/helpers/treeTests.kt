/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.readWithTemplates
import org.jetbrains.amper.frontend.api.asTrace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.PathCtx
import org.jetbrains.amper.frontend.contexts.tryReadMinimalModule
import org.jetbrains.amper.frontend.tree.TreeNode
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.jsonDump
import org.jetbrains.amper.frontend.tree.mergeTrees
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.refineTree
import org.jetbrains.amper.frontend.tree.resolveReferences
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.test.golden.trimTrailingWhitespacesAndEmptyLines
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.test.assertNotNull
import kotlin.test.fail

/**
 * Test that the passed module file is read correctly.
 */
fun FrontendTestCaseBase.testModuleRead(
    caseName: String,
    types: SchemaTypingContext = SchemaTypingContext(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    testCase = this,
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readModule,
).doTest()

/**
 * Tests that the passed module file is read and refined correctly.
 */
fun FrontendTestCaseBase.testRefineModule(
    caseName: String,
    selectedContexts: Contexts,
    types: SchemaTypingContext = SchemaTypingContext(),
    expectPostfix: String = ".result.json",
    withDefaults: Boolean = false,
) = TreeTestRun(
    caseName = caseName,
    testCase = this,
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readAndRefineModule(selectedContexts, withDefaults),
).doTest()

/**
 * Tests that the passed module file with templates is read and refined correctly.
 */
fun FrontendTestCaseBase.testRefineModuleWithTemplates(
    caseName: String,
    selectedContexts: (VirtualFile) -> Contexts,
    types: SchemaTypingContext = SchemaTypingContext(),
    expectPostfix: String = ".result.json",
) = TreeTestRun(
    caseName = caseName,
    testCase = this,
    types = types,
    expectPostfix = expectPostfix,
    treeBuilder = readAndRefineModuleWithTemplates(selectedContexts),
    dumpPathContexts = true,
).doTest()

/**
 * Tests that the diagnostics created during module read are the same as in the file.
 */
fun FrontendTestCaseBase.diagnoseModuleRead(
    caseName: String,
    types: SchemaTypingContext = SchemaTypingContext(),
) = DiagnosticsTreeTestRun(
    caseName = caseName,
    testCase = this,
    types = types,
    treeBuilder = readModule,
).doTest()

/**
 * Test run that:
 * 1. Reads [TreeNode] with [treeBuilder].
 * 2. Checks that there are no diagnostics reported afterward.
 * 3. Compares tree dump with `[base]/[caseName][expectPostfix]` file contents.
 */
internal open class TreeTestRun(
    caseName: String,
    testCase: FrontendTestCaseBase,
    protected val types: SchemaTypingContext,
    override val expectPostfix: String,
    protected val treeBuilder: BuildCtx.(VirtualFile) -> TreeNode,
    protected val dumpPathContexts: Boolean = false,
) : FrontendTest(caseName, testCase) {

    protected val diagnostics get() = problemReporter.problems

    protected val buildCtx = BuildCtx(
        problemReporter = problemReporter,
        types = types,
        pathResolver = pathResolver,
    )

    protected fun doReadTree(inputPath: Path): TreeNode {
        val inputVirtual = buildCtx.pathResolver.loadVirtualFile(inputPath)
        val readTree = buildCtx.treeBuilder(inputVirtual)
        assertNotNull(readTree)
        return readTree
    }

    override fun getInputContent(): String {
        val readTree = doReadTree(inputPath)
        assertNoReportedErrors()
        return readTree.jsonDump(base.absolute()) { dumpPathContexts || it !is PathCtx }
    }
}

/**
 * Test run that:
 * 1. Reads [TreeNode] with [treeBuilder].
 * 2. Marks the PSI text with diagnostics.
 * 3. Compares the result with input.
 */
internal open class DiagnosticsTreeTestRun(
    caseName: String,
    testCase: FrontendTestCaseBase,
    types: SchemaTypingContext,
    treeBuilder: BuildCtx.(VirtualFile) -> TreeNode,
    dumpPathContexts: Boolean = false,
) : TreeTestRun(caseName, testCase, types, "", treeBuilder, dumpPathContexts) {

    override val expectPostfix: String = ".yaml"

    override fun getInputContent(): String {
        // Read tree to fill the diagnostics
        doReadTree(inputPath)

        val virtualFile = buildCtx.pathResolver.loadVirtualFile(inputPath)
        val psiFile = buildCtx.pathResolver.toPsiFile(virtualFile) ?: fail("PSI file for $inputPath not found")
        return annotateTextWithDiagnostics(inputPath, psiFile.text, diagnostics) { it }
            .trimTrailingWhitespacesAndEmptyLines()
    }
}

// Helper function to just read the module.
internal val readModule: BuildCtx.(VirtualFile) -> TreeNode = { readTree(it, moduleAType) }

// Helper function read the module and refine it with selected contexts.
internal fun readAndRefineModule(
    contexts: Contexts,
    withDefaults: Boolean = false,
): BuildCtx.(VirtualFile) -> TreeNode = {
    val minimalModule = tryReadMinimalModule(it)!!
    var tree = readTree(it, moduleAType)
    tree = if (withDefaults) tree.appendDefaultValues() else tree
    tree.refineTree(contexts, minimalModule.combinedInheritance)
        .resolveReferences()
}

// Helper function read the module with templates and refine it with selected contexts.
internal fun readAndRefineModuleWithTemplates(contexts: (VirtualFile) -> Contexts): BuildCtx.(VirtualFile) -> TreeNode =
    {
        val minimalModule = tryReadMinimalModule(it)!!
        val ownedTrees = readWithTemplates(minimalModule, it, PathCtx(it, it.asPsi().asTrace()))
        val resultTree = mergeTrees(ownedTrees)
        resultTree.refineTree(contexts(it), minimalModule.combinedInheritance)
    }