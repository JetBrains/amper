package org.jetbrains.deft.ide.externalTool

import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.Disposable
import com.intellij.openapi.externalSystem.autoimport.*
import com.intellij.openapi.externalSystem.importing.ImportSpecBuilder
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataImportListener
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.observable.properties.AtomicBooleanProperty
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import org.jetbrains.deft.ide.POT_FILE_NAME
import org.jetbrains.deft.ide.TEMPLATE_FILE_SUFFIX
import org.jetbrains.plugins.gradle.util.GradleConstants
import java.io.IOException
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import kotlin.io.path.name

/**
 * This solution is based on the [MavenProjectsAware] and [ProjectAware] classes from IntelliJ.
 * It doesn't look like a proper way to do things, but it works.
 */
class DeftExternalSystemProjectAware(
    private val project: Project,
    private val externalProjectPath: String
) : ExternalSystemProjectAware {
    private val systemId = ProjectSystemId("Deft", "Deft")
    private val isImportCompleted = AtomicBooleanProperty(true)

    override val projectId: ExternalSystemProjectId
        get() = ExternalSystemProjectId(systemId, externalProjectPath)

    override val settingsFiles: Set<String>
        get() = getPotFiles().map { FileUtil.toCanonicalPath(it.toString()) }.toSet()

    init {
        project.messageBus.connect().subscribe(
            ProjectDataImportListener.TOPIC,
            object : ProjectDataImportListener {
                override fun onImportStarted(projectPath: String?) {
                    isImportCompleted.set(false)
                }

                override fun onImportFinished(projectPath: String?) {
                    isImportCompleted.set(true)
                }
            }
        )
    }

    override fun reloadProject(context: ExternalSystemProjectReloadContext) {
        FileDocumentManager.getInstance().saveAllDocuments()
        val importSpec = ImportSpecBuilder(project, GradleConstants.SYSTEM_ID)
        if (!context.isExplicitReload) {
            importSpec.dontReportRefreshErrors()
            importSpec.dontNavigateToError()
        }
        if (!project.isTrusted()) {
            importSpec.usePreviewMode()
        }
        ExternalSystemUtil.refreshProject(externalProjectPath, importSpec)
    }

    override fun subscribe(listener: ExternalSystemProjectListener, parentDisposable: Disposable) {
        isImportCompleted.afterReset(parentDisposable) { listener.onProjectReloadStart() }
        isImportCompleted.afterSet(parentDisposable) { listener.onProjectReloadFinish(ExternalSystemRefreshStatus.SUCCESS) }
    }

    private fun getPotFiles(): Set<Path> {
        val files = mutableSetOf<Path>()
        try {
            Files.walkFileTree(Paths.get(externalProjectPath), object : SimpleFileVisitor<Path>() {
                override fun visitFile(path: Path, attrs: BasicFileAttributes): FileVisitResult {
                    if (path.name.endsWith(POT_FILE_NAME) || path.name.endsWith(TEMPLATE_FILE_SUFFIX)) {
                        val file = path.toFile()
                        if (file.isFile) files.add(path)
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (_: IOException) {}

        return files
    }
}
