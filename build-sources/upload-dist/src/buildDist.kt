/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.stdlib.hashing.sha256String
import org.jetbrains.amper.wrapper.AmperWrappers
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.outputStream
import kotlin.io.path.readBytes

internal data class Distribution(
    val cliTgz: Path,
    val wrappers: List<Path>,
)

internal suspend fun ExecuteOnChangedInputs.buildDist(
    outputDir: Path,
    cliRuntimeClasspath: List<Path>,
    extraClasspaths: List<NamedClasspath>,
): Distribution {
    val result = execute(
        id = "build-dist",
        configuration = mapOf(
            "extraClasspaths" to extraClasspaths.joinToString { it.name },
        ),
        inputs = buildList {
            addAll(cliRuntimeClasspath)
            extraClasspaths.forEach { addAll(it.classpath) }
        },
    ) {
        val cliTgz = outputDir.resolve("cli.tgz")

        println("Writing CLI distribution to $cliTgz")
        cliTgz.writeDistTarGz(cliRuntimeClasspath, extraClasspaths)

        val wrappers = AmperWrappers.generate(
            targetDir = outputDir,
            amperVersion = AmperBuild.mavenVersion,
            amperDistTgzSha256 = cliTgz.readBytes().sha256String(),
            coroutinesDebugVersion = cliRuntimeClasspath.coroutinesDebugVersion(),
        )

        ExecuteOnChangedInputs.ExecutionResult(outputs = listOf(cliTgz) + wrappers)
    }
    return Distribution(cliTgz = result.outputs[0], wrappers = result.outputs.drop(1))
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
