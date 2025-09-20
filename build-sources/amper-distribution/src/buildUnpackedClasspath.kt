/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.Classpath
import org.jetbrains.amper.Input
import org.jetbrains.amper.Output
import org.jetbrains.amper.TaskAction
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.stdlib.hashing.sha256String
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.writeText

@TaskAction
fun buildUnpackedClasspath(
    @Output outputDir: Path,
    @Input baseClasspath: Classpath,
    @Input extraClasspaths: Map<String, Classpath> = emptyMap(),
    subdirectoryName: String?,
    jarListFileName: String?,
) {
    val targetDir = subdirectoryName?.let { outputDir / it } ?: outputDir
    cleanDirectory(targetDir)
    val classpaths = buildMap {
        put("lib", baseClasspath)
        putAll(extraClasspaths)
    }
    classpaths.forEach { (name, paths) ->
        val dir = (targetDir / name).createDirectory()
        // some jars have the exact same filename even though they don't come from the same artifact
        val alreadySeenFilenames = mutableSetOf<String>()
        for (path in paths.resolvedFiles) {
            val alreadyExists = !alreadySeenFilenames.add(path.name)
            val filename = if (alreadyExists) {
                "${path.nameWithoutExtension}-${path.pathString.sha256String().take(8)}.${path.extension}"
            } else {
                path.name
            }
            path.copyTo(dir.resolve(filename))
        }
    }
    jarListFileName?.let { fileName ->
        targetDir.resolve(fileName).writeText(baseClasspath.resolvedFiles.joinToString("\n") { it.name })
    }
}