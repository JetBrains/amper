package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project
import org.jetbrains.deft.proto.gradle.base.BindingPluginPart

class DeftGradleExtension(
    private val project: Project,
) {
    var useDeftLayout: Boolean = false
        set(value) {
            field = value
            project.plugins.findPlugin(BindingProjectPlugin::class.java)?.onDefExtensionChanged()
        }
}

val BindingPluginPart.useDeftLayout
    get() = if (hasGradleScripts)
        project.extensions
            .findByType(DeftGradleExtension::class.java)
            ?.useDeftLayout
            ?: false
    else true