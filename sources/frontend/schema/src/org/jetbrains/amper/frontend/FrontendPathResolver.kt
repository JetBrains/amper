/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.mock.MockApplication
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.DummyProject
import org.jetbrains.amper.frontend.schemaConverter.psi.standalone.getPsiRawModel
import java.io.Reader
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.reader

data class FrontendPathResolver(
    val project: Project = DummyProject.instance,

    @Deprecated("could be used directly in tests only, " +
        "all other places should resolve file content via PsiFile, see field  path2PsiFile")
    val path2Reader: (Path) -> Reader? = {
        // TODO Replace default reader by something other.
        // TODO Report non existing file.
        if (it.exists()) it.reader() else null
    },

    val path2PsiFile: (Path) -> PsiFile? = { path ->
        val application = ApplicationManager.getApplication()
        application.runReadAction(Computable {
            if (application != null && application !is MockApplication && project != DummyProject.instance) {
                val vfsFile = VirtualFileManager.getInstance().findFileByNioPath(path)
                vfsFile?.let { PsiManager.getInstance(project).findFile(it) }
            } else {
                path2Reader(path)?.let { getPsiRawModel(it) }
            }
        })
    },
)