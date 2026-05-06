/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.plugins.Classpath
import org.jetbrains.amper.plugins.Input
import org.jetbrains.amper.plugins.Output
import org.jetbrains.amper.plugins.TaskAction
import org.jetbrains.amper.stdlib.hashing.sha256String
import org.jetbrains.amper.wrapper.AmperWrappers
import java.nio.file.Path
import kotlin.io.path.copyTo
import kotlin.io.path.copyToRecursively
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
    @Input extraFilteredClasspaths: Map<String, FilteredClasspath> = emptyMap(),
    @Input thirdPartyStagingDir: Path?,
    subdirectoryName: String?,
    jarListFileName: String?,
) {
    val targetDir = subdirectoryName?.let { outputDir / it } ?: outputDir
    cleanDirectory(targetDir)
    val classpaths = buildMap {
        put("lib", baseClasspath.resolvedFiles)
        extraClasspaths.forEach { (key, classpath) ->
            put(key, classpath.resolvedFiles)
        }
        extraFilteredClasspaths.forEach { (key, classpath) ->
            put(key, classpath.resolvedFiles)
        }
    }
    classpaths.forEach { (name, paths) ->
        val dir = (targetDir / name).createDirectory()
        // some jars have the exact same filename even though they don't come from the same artifact
        val alreadySeenFilenames = mutableSetOf<String>()
        for (path in paths) {
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

    AmperWrappers.generateLaunchers(targetDir / "bin")

    thirdPartyStagingDir?.copyToRecursively(
        target = targetDir,
        followLinks = false,
        overwrite = false,
    )
}