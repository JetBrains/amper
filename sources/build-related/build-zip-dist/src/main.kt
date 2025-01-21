/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ReplacePrintlnWithLogging")

import kotlinx.coroutines.runBlocking
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.jetbrains.amper.cli.AmperBuildOutputRoot
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.io.File
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

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
    val unixWrapperTemplate = Path("resources/wrappers/amper.template.sh").toAbsolutePath()
    val windowsWrapperTemplate = Path("resources/wrappers/amper.template.bat").toAbsolutePath()

    // fake build output root under our task
    val buildOutputRoot = AmperBuildOutputRoot(taskOutputDirectory)
    val executeOnChangedInputs = ExecuteOnChangedInputs(buildOutputRoot)

    runBlocking {
        executeOnChangedInputs.execute("build-dist", emptyMap(), cliRuntimeClasspath) {
            val cliTgz = taskOutputDirectory.resolve("cli.tgz")

            println("Writing CLI distribution to $cliTgz")
            cliTgz.writeDistTarGz(cliRuntimeClasspath)

            val wrappers = AmperWrappers.generateWrappers(
                targetDir = taskOutputDirectory,
                cliDistTgz = cliTgz,
                unixTemplate = unixWrapperTemplate,
                windowsTemplate = windowsWrapperTemplate,
            )

            ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(cliTgz) + wrappers)
        }
    }
}

private fun Path.writeDistTarGz(cliRuntimeClasspath: List<Path>) {
    TarArchiveOutputStream(GZIPOutputStream(outputStream().buffered())).use { tarStream ->
        cliRuntimeClasspath.sortedBy { it.name }.forEach { path ->
            val entry = TarArchiveEntry(path, "lib/${path.name}")
            tarStream.putArchiveEntry(entry)
            path.inputStream().use { input -> input.copyTo(tarStream) }
            tarStream.closeArchiveEntry()
        }
    }
}
