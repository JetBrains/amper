/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.helpers

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.project.AmperProjectContext
import org.jetbrains.amper.frontend.project.StandaloneAmperProjectContext
import org.jetbrains.amper.problems.reporting.ProblemReporter
import java.nio.file.Path
import kotlin.test.fail

class TestSystemInfo(
    private val predefined: SystemInfo.Os
) : SystemInfo {
    override fun detect() = predefined
}

data class TestProjectContext(
    override val projectRootDir: VirtualFile,
    override val amperModuleFiles: List<VirtualFile>,
    override val frontendPathResolver: FrontendPathResolver,
) : AmperProjectContext {
    override val pluginModuleFiles: List<VirtualFile> = emptyList()
    override val projectBuildDir: Path get() = projectRootDir.toNioPath()
    override var projectVersionsCatalog: VersionCatalog? = null
}

/**
 * Creates a [FrontendPathResolver] for tests.
 *
 * The difference with a real [FrontendPathResolver] is that it strips out diagnostic annotations from test PSI files.
 *
 * **Important:** any other test must also use a similarly-configured [FrontendPathResolver] because the mock IntellIJ
 * project inside is a singleton and must be initialized the same way.
 */
@Suppress("TestFunctionName") // factory function convention
internal fun TestFrontendPathResolver() = FrontendPathResolver(
    intelliJApplicationConfigurator = ModifiablePsiIntelliJApplicationConfigurator,
    transformPsiFile = PsiFile::removeDiagnosticAnnotations,
)

/**
 * Reads the project context for the given test project, internally using a [TestFrontendPathResolver].
 *
 * **It is not possible to use the real project context discovery and this function in the same JVM, because the mock
 * IntelliJ project is a singleton that must be consistently configured.**
 *
 * * This is different from creating a [TestProjectContext], which is a fake context with a manually constructed list
 *   of modules. This function reads the project context from a real `project.yaml`/`module.yaml` file.
 * * This is also different from a plain [StandaloneAmperProjectContext.create] call, because the [FrontendPathResolver]
 *   used here strips out diagnostic annotations from test PSI files and uses a special configurator for the mock
 *   IntelliJ project.
 *
 * @see TestFrontendPathResolver
 */
context(problemReporter: ProblemReporter)
internal fun readProjectContextWithTestFrontendResolver(testProjectDir: Path): AmperProjectContext {
    val pathResolver = TestFrontendPathResolver()
    val virtualProjectDir = pathResolver.loadVirtualFile(testProjectDir)
    return StandaloneAmperProjectContext.create(virtualProjectDir, buildDir = null, pathResolver)
        ?: fail("Invalid project root: $testProjectDir")
}