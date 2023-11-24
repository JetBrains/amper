/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.gradle

import org.gradle.api.Project
import org.jetbrains.amper.frontend.Layout

@Deprecated("Scheduled to remove")
class AmperGradleExtension(
    private val project: Project,
) {
    @Deprecated("Scheduled to remove")
    var layout: Layout? = null

    @Deprecated("Scheduled to remove")
    var useAmperLayout: Boolean = false
}