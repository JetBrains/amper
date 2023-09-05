package org.jetbrains.deft.proto.gradle

import org.gradle.api.Project
import org.jetbrains.deft.proto.frontend.Layout


@Deprecated("Scheduled to remove")
class DeftGradleExtension(
    private val project: Project,
) {
    @Deprecated("Scheduled to remove")
    var layout: Layout? = null

    @Deprecated("Scheduled to remove")
    var useDeftLayout: Boolean = false
}