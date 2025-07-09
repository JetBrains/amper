/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.path
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories
import kotlin.io.path.createDirectory
import kotlin.io.path.div
import kotlin.io.path.name
import kotlin.io.path.pathString
import kotlin.io.path.writeText

suspend fun main(args: Array<String>) = BuildUnpackedDistCommand().main(args)

class BuildUnpackedDistCommand : CacheableTaskCommand() {

    private val classpath by option("--classpath").classpath().required()
    private val targetDir by option("--target-dir").path(mustExist = false, canBeFile = false).required()
    private val extraClasspaths by option("--extra-dir").namedClasspath().multiple()

    private val jarListFile by option("--jar-list-file")

    override suspend fun ExecuteOnChangedInputs.runCached() {
        execute(
            id = "build-unpacked-dist",
            configuration = mapOf("targetDir" to targetDir.pathString),
            inputs = classpath,
        ) {
            cleanDirectory(targetDir)
            val libDir = targetDir / "lib"
            libDir.createDirectories()
            println("Copying classpath files to $libDir")

            for (path in classpath) {
                path.copyTo(libDir.resolve(path.fileName))
            }
            extraClasspaths.forEach { (name, paths) ->
                val dir = (targetDir / name).createDirectory()
                for (path in paths) {
                    path.copyTo(dir.resolve(path.fileName))
                }
            }
            jarListFile?.let { listFile ->
                targetDir.resolve(listFile).writeText(classpath.joinToString("\n") { it.name })
            }

            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(targetDir))
        }
    }
}
