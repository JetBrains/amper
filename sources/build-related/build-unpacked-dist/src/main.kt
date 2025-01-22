/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

suspend fun main(args: Array<String>) = BuildUnpackedDistCommand().main(args)

class BuildUnpackedDistCommand : CacheableTaskCommand() {

    private val cliRuntimeClasspath by option("--classpath").classpath().required()
    private val extraClasspaths by option("--extra-dir").namedClasspath().multiple()

    override suspend fun ExecuteOnChangedInputs.runCached() {
        execute(
            id = "build-unpacked-dist",
            configuration = mapOf("extraDirs" to extraClasspaths.joinToString("|")),
            inputs = cliRuntimeClasspath + extraClasspaths.flatMap { it.classpath },
        ) {
            val distDir = taskOutputDirectory.resolve("dist")
            cleanDirectory(distDir)
            println("Copying dist files to $distDir")

            val libDir = distDir.resolve("lib").createDirectories()
            for (path in cliRuntimeClasspath) {
                path.copyTo(libDir.resolve(path.fileName))
            }

            extraClasspaths.forEach { (dirName, paths) ->
                val dir = distDir.resolve(dirName).createDirectories()
                paths.forEach { path ->
                    path.copyTo(dir.resolve(path.fileName))
                }
            }

            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(distDir))
        }
    }
}
