/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

fun main(args: Array<String>) {
    val taskOutput = Path.of(args.single())
    taskOutput.resolve("build.properties").writeText("build.props")

    val s = taskOutput.resolve("subDir").resolve("some")
    s.createDirectories()
    s.resolve("my.file").writeText("my")
}
