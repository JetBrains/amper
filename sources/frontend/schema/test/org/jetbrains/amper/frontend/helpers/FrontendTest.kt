/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import com.intellij.psi.PsiFile
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.problems.reporting.CollectingProblemReporter
import org.jetbrains.amper.test.golden.GoldFileTest
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.readText

/**
 * [GoldFileTest] that always has the input file, that is used to read the model.
 * 
 * See [GoldFileTest] for the assertion logic.
 */
abstract class FrontendTest(
    caseName: String,
    private val testCase: FrontendTestCaseBase,
) : GoldFileTest(caseName, testCase.base) {
    protected open val inputPostfix: String = ".yaml"
    protected open val inputPath: Path get() = base.resolve(caseName + inputPostfix).absolute()

    protected val problemReporter = CollectingProblemReporter()

    protected val pathResolver = FrontendPathResolver(
        intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
        transformPsiFile = PsiFile::removeDiagnosticAnnotations
    )

    protected val buildDir by lazy { testCase.buildDir.absolute() }

    protected val inputFile by lazy { pathResolver.loadVirtualFile(inputPath) }
    protected val buildDirFile by lazy { pathResolver.loadVirtualFile(buildDir) }

    /**
     * Default test project context.
     */
    protected val projectContext = TestProjectContext(
        projectRootDir = buildDirFile,
        amperModuleFiles = listOf(inputFile),
        frontendPathResolver = pathResolver,
    )
    
    override fun getInputContent() = inputPath.readText()

    /**
     * Check that there are no reported errors in the [problemReporter].
     */
    protected fun assertNoReportedErrors() = assert(problemReporter.problems.isEmpty()) {
        val concatenatedMessages = problemReporter.problems.joinToString(separator = "\n\t") { it.message }
        "Expected no errors, but got \n\t$concatenatedMessages\n"
    }
}