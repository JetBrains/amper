/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import java.nio.file.Path

data class TaskOutputRoot(val path: Path) {
    init {
        require(path.isAbsolute) {
            "Task output path should be always absolute: $path"
        }
    }
}
