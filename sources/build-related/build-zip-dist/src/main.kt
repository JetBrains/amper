/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ReplacePrintlnWithLogging")

import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.parameters.options.multiple
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.jetbrains.amper.util.ExecuteOnChangedInputs
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.Path
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream

/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

suspend fun main(args: Array<String>) = BuildTgzDistCommand().main(args)

class BuildTgzDistCommand : CacheableTaskCommand() {

    private val cliRuntimeClasspath by option("--classpath").classpath().required()
    private val extraClasspaths by option("--extra-dir").namedClasspath().multiple()

    override suspend fun ExecuteOnChangedInputs.runCached() {
        val unixWrapperTemplate = Path("resources/wrappers/amper.template.sh").toAbsolutePath()
        val windowsWrapperTemplate = Path("resources/wrappers/amper.template.bat").toAbsolutePath()

        execute("build-tgz-dist", emptyMap(), cliRuntimeClasspath) {
            val cliTgz = taskOutputDirectory.resolve("cli.tgz")

            println("Writing CLI distribution to $cliTgz")
            cliTgz.writeDistTarGz(cliRuntimeClasspath, extraClasspaths)

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

private fun Path.writeDistTarGz(cliRuntimeClasspath: List<Path>, extraClasspaths: List<NamedClasspath>) {
    TarArchiveOutputStream(GZIPOutputStream(outputStream().buffered())).use { tarStream ->
        tarStream.writeDir(cliRuntimeClasspath, targetDirName = "lib")
        extraClasspaths.forEach { (name, paths) ->
            tarStream.writeDir(paths, targetDirName = name)
        }
    }
}

private fun TarArchiveOutputStream.writeDir(files: List<Path>, targetDirName: String) {
    files.sortedBy { it.name }.forEach { path ->
        writeFile(path, "$targetDirName/${path.name}")
    }
}

private fun TarArchiveOutputStream.writeFile(file: Path, pathInTar: String) {
    val entry = TarArchiveEntry(file, pathInTar)
    putArchiveEntry(entry)
    file.inputStream().use { input -> input.copyTo(this) }
    closeArchiveEntry()
}
