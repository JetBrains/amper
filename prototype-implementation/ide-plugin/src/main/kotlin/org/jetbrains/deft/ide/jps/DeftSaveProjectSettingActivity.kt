package org.jetbrains.deft.ide.jps

import com.intellij.ide.SaveAndSyncHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * It looks like IsUpToDateCheckStartupActivity expects settings on disk after project opening.
 * This is a dirty hack to make it work. Investigate the problem and fix it properly ASAP.
 */
@Suppress("UnstableApiUsage")
class DeftSaveProjectSettingActivity : ProjectActivity {
    override suspend fun execute(project: Project) {
        ApplicationManager.getApplication().invokeLater {
            val task = SaveAndSyncHandler.SaveTask(project = project, forceSavingAllSettings = true)
            SaveAndSyncHandler.getInstance().scheduleSave(task, forceExecuteImmediately = true)
        }
    }
}
