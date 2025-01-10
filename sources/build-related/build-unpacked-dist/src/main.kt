/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.core.extract.cleanDirectory
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.File
import kotlin.io.path.Path
import kotlin.io.path.copyTo
import kotlin.io.path.createDirectories

fun main(args: Array<String>) {
    val (taskOutputDirectoryString, cliRuntimeClasspathString) = args

    val taskOutputDirectory = Path(taskOutputDirectoryString)
    val cliRuntimeClasspath = cliRuntimeClasspathString
        .split(File.pathSeparator)
        .map { Path(it) }
        .also {
            check(it.size > 3) {
                "cli runtime classpath must contain at least 3 elements, but got ${it.size}: $it"
            }
        }

    // fake build output root under our task
    val buildOutputRoot = AmperBuildOutputRoot(taskOutputDirectory)
    val executeOnChangedInputs = ExecuteOnChangedInputs(buildOutputRoot)

    runBlocking {
        executeOnChangedInputs.execute("build-unpacked-dist", emptyMap(), cliRuntimeClasspath) {
            val distDir = taskOutputDirectory.resolve("dist")
            cleanDirectory(distDir)
            println("Copying dist files to $distDir")

            val libDir = distDir.resolve("lib")
            libDir.createDirectories()

            for (path in cliRuntimeClasspath) {
                path.copyTo(libDir.resolve(path.fileName))
            }

            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(distDir))
        }
    }
}
