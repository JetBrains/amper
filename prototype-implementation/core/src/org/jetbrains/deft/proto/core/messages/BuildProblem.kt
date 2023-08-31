package org.jetbrains.deft.proto.core.messages

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
