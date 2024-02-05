/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.mock.MockApplication
import com.intellij.mock.MockProject
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.initMockProject
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

class FrontendPathResolver(
    val project: Project? = null,
    @TestOnly
    private val transformPsiFile: (PsiFile) -> PsiFile = { it },
    /**
     * Can be used to register additional extension points needed only for tests (e.g., to modify PSI).
     */
    @TestOnly
    private val intelliJApplicationConfigurator: IntelliJApplicationConfigurator = IntelliJApplicationConfigurator(),
) {
    fun path2PsiFile(path: Path): PsiFile? {
        val actualProject = project ?: initMockProject(intelliJApplicationConfigurator)
        val application = ApplicationManager.getApplication()
        return application.runReadAction(Computable {
            val vfsFile = VirtualFileManager.getInstance().findFileByNioPath(path)
            vfsFile?.let { PsiManager.getInstance(actualProject).findFile(it) }?.let(transformPsiFile)
        })
    }
}

@TestOnly
open class IntelliJApplicationConfigurator {
    open fun registerApplicationExtensions(application: MockApplication) {}
    open fun registerProjectExtensions(project: MockProject) {}
}