package org.jetbrains.deft.ide.jps

import com.intellij.ide.impl.runUnderModalProgressIfIsEdt
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectImport.ProjectOpenProcessor

@Suppress("UnstableApiUsage")
class DeftOpenProjectProcessor() : ProjectOpenProcessor() {

    private val projectProvider = DefOpenProjectProvider()

    override val name: String
        get() = "Deft"

    override fun canOpenProject(file: VirtualFile): Boolean = projectProvider.canOpenProject(file)

    override fun doOpenProject(
        virtualFile: VirtualFile,
        projectToClose: Project?,
        forceOpenInNewFrame: Boolean
    ): Project? = runUnderModalProgressIfIsEdt {
        projectProvider.openProject(virtualFile, projectToClose, forceOpenInNewFrame)
    }
}
