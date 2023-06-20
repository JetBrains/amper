package org.jetbrains.deft.ide.externalTool

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerEx
import com.intellij.openapi.project.Project

class DeftProjectsWatcher : ExternalSystemSettingsListenerEx {
    override fun onProjectsLinked(
        project: Project,
        manager: ExternalSystemManager<*, *, *, *, *>,
        settings: Collection<ExternalProjectSettings>
    ) {
        val projectTracker = ExternalSystemProjectTracker.getInstance(project)
        settings.forEach {
            projectTracker.register(DeftExternalSystemProjectAware(project, it.externalProjectPath))
        }
    }
}
