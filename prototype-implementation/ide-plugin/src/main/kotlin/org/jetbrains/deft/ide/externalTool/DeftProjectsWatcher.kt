package org.jetbrains.deft.ide.externalTool

import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectTracker
import com.intellij.openapi.externalSystem.settings.ExternalProjectSettings
import com.intellij.openapi.externalSystem.settings.ExternalSystemSettingsListenerEx
import com.intellij.openapi.project.Project
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings
import org.jetbrains.plugins.gradle.util.GradleConstants

class DeftProjectsWatcher : ExternalSystemSettingsListenerEx {
    override fun onProjectsLinked(
        project: Project,
        manager: ExternalSystemManager<*, *, *, *, *>,
        settings: Collection<ExternalProjectSettings>
    ) {
        val projectTracker = ExternalSystemProjectTracker.getInstance(project)
        if (manager.systemId == GradleConstants.SYSTEM_ID) {
            settings.filterIsInstance<GradleProjectSettings>().forEach {
                projectTracker.register(DeftExternalSystemProjectAware(project, it.externalProjectPath))
            }
        }
    }
}
