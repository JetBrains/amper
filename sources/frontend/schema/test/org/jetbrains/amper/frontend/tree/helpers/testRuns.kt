/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.tree.helpers

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.aomBuilder.readWithTemplates
import org.jetbrains.amper.frontend.api.trace
import org.jetbrains.amper.frontend.contexts.Contexts
import org.jetbrains.amper.frontend.contexts.PathCtx
import org.jetbrains.amper.frontend.contexts.tryReadMinimalModule
import org.jetbrains.amper.frontend.schema.helper.BaseFrontendTestRun
import org.jetbrains.amper.frontend.schema.helper.ModifiablePsiIntelliJApplicationConfigurator
import org.jetbrains.amper.frontend.schema.helper.annotateTextWithDiagnostics
import org.jetbrains.amper.frontend.schema.helper.removeDiagnosticAnnotations
import org.jetbrains.amper.frontend.tree.MergedTree
import org.jetbrains.amper.frontend.tree.TreeValue
import org.jetbrains.amper.frontend.tree.appendDefaultValues
import org.jetbrains.amper.frontend.tree.jsonDump
import org.jetbrains.amper.frontend.tree.reading.readTree
import org.jetbrains.amper.frontend.tree.refineTree
import org.jetbrains.amper.frontend.types.SchemaTypingContext
import org.jetbrains.amper.test.golden.GoldenTest
import org.jetbrains.amper.test.golden.readContentsAndReplace
import org.jetbrains.amper.test.golden.trimTrailingWhitespacesAndEmptyLines
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail


/**
 * Test run that:
 * 1. Reads [TreeValue] with [treeBuilder].
 * 2. Checks that there are no diagnostics reported afterward.
 * 3. Compares tree dump with `[base]/[caseName][expectPostfix]` file contents.
 */
internal open class TreeTestRun(
    caseName: String,
    override val base: Path,
    protected val types: SchemaTypingContext,
    override val expectPostfix: String,
    protected val treeBuilder: BuildCtx.(VirtualFile) -> TreeValue<*>?,
    protected val dumpPathContexts: Boolean = false,
) : BaseFrontendTestRun(caseName) {

    protected val diagnostics get() = ctx.problemReporter.getDiagnostics()

    protected val buildCtx = BuildCtx(
        problemReporterCtx = ctx,
        types = types,
        pathResolver = FrontendPathResolver(
            transformPsiFile = PsiFile::removeDiagnosticAnnotations,
            intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
        ),
    )

    protected fun doReadTree(inputPath: Path): TreeValue<*> {
        val inputVirtual = buildCtx.pathResolver.loadVirtualFile(inputPath)
        val readTree = buildCtx.treeBuilder(inputVirtual)
        assertNotNull(readTree)
        return readTree
    }

    override fun GoldenTest.getInputContent(inputPath: Path): String {
        val readTree = doReadTree(inputPath)
        assertTrue(
            diagnostics.isEmpty(),
            "Expected no problems after reading, but got:\n\t${diagnostics.joinToString("\n\t")}"
        )
        return readTree.jsonDump(base.absolute()) { dumpPathContexts || it !is PathCtx }
    }
}

/**
 * Test run that:
 * 1. Reads [TreeValue] with [treeBuilder].
 * 2. Marks the psi text with diagnostics.
 * 3. Compares the result with input.
 */
internal open class DiagnosticsTreeTestRun(
    caseName: String,
    base: Path,
    types: SchemaTypingContext,
    treeBuilder: BuildCtx.(VirtualFile) -> TreeValue<*>?,
    dumpPathContexts: Boolean = false,
) : TreeTestRun(caseName, base, types, "", treeBuilder, dumpPathContexts) {

    override val expectPostfix: String = ".yaml"
    override val expectAmperPostfix: String = ".amper"

    override fun GoldenTest.getInputContent(inputPath: Path): String = with(buildCtx) {
        doReadTree(inputPath)
        annotateTextWithDiagnostics(inputPath, inputPath.asPsi().text!!, diagnostics) { it }
            .trimTrailingWhitespacesAndEmptyLines()
    }

    override fun GoldenTest.getExpectContent(expectedPath: Path) =
        readContentsAndReplace(expectedPath, base).trimTrailingWhitespacesAndEmptyLines()
}

// Helper function to just read the module.
internal val readModule: BuildCtx.(VirtualFile) -> TreeValue<*> = {
    readTree(it, moduleAType) ?: fail("No tree for $it")
}

// Helper function read the module and refine it with selected contexts.
internal fun readAndRefineModule(
    contexts: Contexts,
    withDefaults: Boolean = false,
): BuildCtx.(VirtualFile) -> TreeValue<*> = {
    val minimalModule = tryReadMinimalModule(it)!!
    var tree: MergedTree = readTree(it, moduleAType) as? MergedTree ?: fail("No tree for $it")
    tree = if (withDefaults) tree.appendDefaultValues() else tree
    tree.refineTree(contexts, minimalModule.combinedInheritance)
}

// Helper function read the module with templates and refine it with selected contexts.
internal fun readAndRefineModuleWithTemplates(contexts: (VirtualFile) -> Contexts): BuildCtx.(VirtualFile) -> TreeValue<*> = {
    val minimalModule = tryReadMinimalModule(it)!!
    val ownedTrees = readWithTemplates(minimalModule, it, PathCtx(it, it.asPsi().trace)) ?: fail("No tree for $it")
    val resultTree = treeMerger.mergeTrees(ownedTrees)
    resultTree.refineTree(contexts(it), minimalModule.combinedInheritance)
}