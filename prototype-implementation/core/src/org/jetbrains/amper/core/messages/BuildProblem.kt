/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core.messages

import java.nio.file.Path

enum class Level {
    Warning,
    Error,
}

data class BuildProblem(
    val message: String,
    val level: Level,
    val file: Path? = null,
    val line: Int? = null,
)

fun BuildProblem.render() = "[$level] $message"
