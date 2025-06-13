/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
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

    val project: Project by lazy {
        project ?: MockProjectInitializer.initMockProject(intelliJApplicationConfigurator)
    }

    fun toPsiFile(file: VirtualFile): PsiFile? {
        val actualProject = project
        val application = ApplicationManager.getApplication()
        return application.runReadAction(Computable {
            PsiManager.getInstance(actualProject).findFile(file)?.let(transformPsiFile)
        })
    }

    fun loadVirtualFile(path: Path): VirtualFile {
        project
        val application = ApplicationManager.getApplication()
        return application.runReadAction(Computable {
            checkNotNull(
                VirtualFileManager.getInstance().findFileByNioPath(path)
            ) { "Virtual file by path $path doesn't exist" }
        })
    }
}
