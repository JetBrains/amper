/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package com.example.amper.plugin.produce_sources

import org.jetbrains.amper.plugins.*
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.writeText

@TaskAction
fun produce(
    @Output kotlin: Path,
    @Output resources: Path,
) {
    val file = kotlin.createDirectories() / "generated.kt"
    file.writeText("""
        package com.example.generated
        
        class Generated
    """.trimIndent())

    val file2 = resources.createDirectories() / "generated.properties"
    file2.writeText("hello=world")
}