/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.amper.intellij.IntelliJApplicationConfigurator
import org.jetbrains.amper.intellij.MockProjectInitializer
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

class FrontendPathResolver(
    project: Project? = null,
    @TestOnly
    private val transformPsiFile: (PsiFile) -> PsiFile = { it },
    /**
     * Can be used to register additional extension points needed only for tests (e.g., to modify PSI).
     */
    @TestOnly
    private val intelliJApplicationConfigurator: IntelliJApplicationConfigurator = IntelliJApplicationConfigurator.EMPTY,
) {
    // Not lazy, because the mock project must be initialized before calling the ApplicationManager singleton
    private val project: Project = project ?: MockProjectInitializer.initMockProject(intelliJApplicationConfigurator)

    fun toPsiFile(file: VirtualFile): PsiFile? = runReadAction {
        PsiManager.getInstance(project).findFile(file)?.let(transformPsiFile)
    }

    fun toPsiDirectory(file: VirtualFile): PsiDirectory? = runReadAction {
        PsiManager.getInstance(project).findDirectory(file)
    }

    fun loadVirtualFile(path: Path): VirtualFile = loadVirtualFileOrNull(path)
        ?: error("Virtual file by path $path doesn't exist")
    
    fun loadVirtualFileOrNull(path: Path): VirtualFile? = runReadAction {
        VirtualFileManager.getInstance().findFileByNioPath(path)
    }

    private inline fun <T> runReadAction(crossinline action: () -> T): T =
        ApplicationManager.getApplication().runReadAction(Computable { action() })
}
