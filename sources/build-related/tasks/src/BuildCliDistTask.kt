@file:Suppress("ReplacePrintlnWithLogging")

import kotlinx.coroutines.runBlocking
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.inputStream
import kotlin.io.path.name

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

object BuildCliDistTask {
    @JvmStatic
    fun main(args: Array<String>) {
        val (taskOutputDirectoryString, cliRuntimeClasspathString) = args

        val taskOutputDirectory = Path.of(taskOutputDirectoryString)
        val cliRuntimeClasspath = cliRuntimeClasspathString
            .split(File.pathSeparator)
            .map { Path.of(it) }
            .also {
                check(it.size > 3) {
                    "cli runtime classpath must contain at least 3 elements, but got ${it.size}: $it"
                }
            }
        val unixWrapperTemplate = Path.of("resources/wrappers/amper.template.sh").toAbsolutePath()
        val windowsWrapperTemplate = Path.of("resources/wrappers/amper.template.bat").toAbsolutePath()

        // fake build output root under our task
        val buildOutputRoot = AmperBuildOutputRoot(taskOutputDirectory)
        val executeOnChangedInputs = ExecuteOnChangedInputs(buildOutputRoot)

        runBlocking {
            executeOnChangedInputs.execute("build-dist", emptyMap(), cliRuntimeClasspath) {
                val cliZip = taskOutputDirectory.resolve("cli.zip")
                println("Writing CLI distribution to $cliZip")
                cliZip.toFile().outputStream().buffered().let { ZipOutputStream(it) }.use { zipStream ->
                    for (path in cliRuntimeClasspath.sortedBy { it.name }) {
                        val entry = ZipEntry("lib/${path.name}").also {
                            it.time = 0
                        }
                        zipStream.putNextEntry(entry)
                        path.inputStream().use { input -> input.copyTo(zipStream) }
                        zipStream.closeEntry()
                    }
                }

                val wrappers = AmperWrappers.generateWrappers(
                    targetDir = taskOutputDirectory,
                    cliZip = cliZip,
                    unixTemplate = unixWrapperTemplate,
                    windowsTemplate = windowsWrapperTemplate,
                )

                ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(cliZip) + wrappers)
            }
        }
    }
}
