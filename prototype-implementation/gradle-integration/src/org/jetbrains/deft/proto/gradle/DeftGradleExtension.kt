package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project

enum class LayoutMode(
    @Deprecated("A way to support old flag")
    internal val flagValue: Boolean
) {
    /**
     * Mode, when Gradle kotlin source sets layout is preserved.
     * `commonMain` directory is renamed to `common`.
     */
    GRADLE(false),

    /**
     * Mode, when `src` and `src@jvm` like platform
     * specific directories layout are used.
     * Non-deft source sets have no directories at all.
     */
    DEFT(true),

    /**
     * Mode, when deft created directories use [DEFT] layout,
     * but all created by user have preserved Gradle layout.
     * `src` directory is renamed to `src@common`.
     */
    COMBINED(false),
}

class DeftGradleExtension(
    private val project: Project,
) {
    var layout: LayoutMode? = null
        set(value) {
            field = value
            project.plugins.findPlugin(BindingProjectPlugin::class.java)?.onDefExtensionChanged()
        }

    @Deprecated("A way to support old flag")
    var useDeftLayout: Boolean
        get() = layout?.flagValue ?: false
        set(value) {
            layout = if (value) LayoutMode.DEFT else LayoutMode.GRADLE
        }
}