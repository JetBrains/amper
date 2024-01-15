package org.jetbrains.amper.tasks

import org.jetbrains.amper.engine.Task
import org.jetbrains.amper.frontend.Platform

interface CompileTask : Task {
    val platform: Platform
}
