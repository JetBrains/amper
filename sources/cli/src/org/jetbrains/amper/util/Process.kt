/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

import java.nio.file.Path


fun fireProcessAndForget(
    command: List<String>,
    workingDir: Path,
    environment: Map<String, String>
) {
    ProcessBuilder(command)
        .directory(workingDir.toFile())
        .also { it.environment().putAll(environment) }
        .start()
}