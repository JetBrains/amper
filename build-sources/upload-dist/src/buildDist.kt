/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.incrementalcache.IncrementalCache
import org.jetbrains.amper.stdlib.hashing.sha256String
import org.jetbrains.amper.wrapper.AmperWrappers
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes

internal data class Distribution(
    val version: String,
    val cliTgz: Path,
    val wrappers: List<Path>,
)

internal suspend fun IncrementalCache.buildDist(
    outputDir: Path,
    cliRuntimeClasspath: List<Path>,
    extraClasspaths: List<NamedClasspath>,
): Distribution {
    val result = execute(
        key = "build-dist",
        inputValues = mapOf(
            "extraClasspaths" to extraClasspaths.joinToString { it.name },
        ),
        inputFiles = buildList {
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
        )

        IncrementalCache.ExecutionResult(outputFiles = listOf(cliTgz) + wrappers)
    }
    return Distribution(
        version = AmperBuild.mavenVersion,
        cliTgz = result.outputFiles[0],
        wrappers = result.outputFiles.drop(1)
    )
}

private fun Path.writeDistTarGz(cliRuntimeClasspath: List<Path>, extraClasspaths: List<NamedClasspath>) {
    TarArchiveOutputStream(GZIPOutputStream(outputStream().buffered())).use { tarStream ->
        tarStream.writeFile(contents = argFileContents(), pathInTar = "amper.args")
        tarStream.writeDir(cliRuntimeClasspath, targetDirName = "lib")
        extraClasspaths.forEach { (name, paths) ->
            tarStream.writeDir(paths, targetDirName = name)
        }
    }
}

private fun argFileContents(): String = commonDefaultJvmArgs().joinToString("\n")

private fun commonDefaultJvmArgs(): List<String> = listOf(
    "-ea",
    "-XX:+EnableDynamicAgentLoading",
    // We need --enable-native-access=ALL-UNNAMED in JRE 24+ because of some JNA usages
    "--enable-native-access=ALL-UNNAMED",
    // We need --sun-misc-unsafe-memory-access=allow in JRE 24+ because of OpenTelemetry
    // See https://github.com/open-telemetry/opentelemetry-java/issues/7219
    "--sun-misc-unsafe-memory-access=allow",
)

private fun TarArchiveOutputStream.writeDir(files: List<Path>, targetDirName: String) {
    // some jars have the exact same filename even though they don't come from the same artifact
    val alreadySeenFilenames = mutableSetOf<String>()
    files.sortedBy { it.name }.forEach { path ->
        val alreadyExists = !alreadySeenFilenames.add(path.name)
        val filename = if (alreadyExists) {
            "${path.nameWithoutExtension}-${path.pathString.sha256String().take(8)}.${path.extension}"
        } else {
            path.name
        }
        writeFile(path, "$targetDirName/$filename")
    }
}

private fun TarArchiveOutputStream.writeFile(file: Path, pathInTar: String) {
    val entry = TarArchiveEntry(file, pathInTar)
    putArchiveEntry(entry)
    file.inputStream().use { input -> input.copyTo(this) }
    closeArchiveEntry()
}

private fun TarArchiveOutputStream.writeFile(contents: String, pathInTar: String) {
    val bytes = contents.encodeToByteArray()
    val entry = TarArchiveEntry(pathInTar).also { it.size = bytes.size.toLong() }
    putArchiveEntry(entry)
    write(bytes)
    closeArchiveEntry()
}
