/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.stdlib.hashing.sha256String
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.pathString
import kotlin.io.path.writeText

suspend fun main(args: Array<String>) = BuildUnpackedDistCommand().main(args)

class BuildUnpackedDistCommand : CacheableTaskCommand() {

    private val classpath by option("--classpath").classpath().required()
    private val targetDir by option("--target-dir").path(mustExist = false, canBeFile = false).required()
    private val extraClasspaths by option("--extra-dir").namedClasspath().multiple()

    private val jarListFile by option("--jar-list-file")

    override suspend fun IncrementalCache.runCached() {
        execute(
            id = "build-unpacked-dist",
            configuration = mapOf(
                "targetDir" to targetDir.pathString,
                "extraClasspaths" to extraClasspaths.joinToString { it.name },
                "jarListFile" to (jarListFile ?: ""),
            ),
            inputs = buildList {
                addAll(classpath)
                extraClasspaths.forEach { addAll(it.classpath) }
            },
        ) {
            cleanDirectory(targetDir)
            (extraClasspaths + NamedClasspath("lib", classpath)).forEach { (name, paths) ->
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
            jarListFile?.let { listFile ->
                targetDir.resolve(listFile).writeText(classpath.joinToString("\n") { it.name })
            }

            IncrementalCache.ExecutionResult(outputs = listOf(targetDir))
        }
    }
}
