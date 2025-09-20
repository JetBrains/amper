/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:Suppress("ReplacePrintlnWithLogging")

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.jetbrains.amper.Classpath
import org.jetbrains.amper.Input
import org.jetbrains.amper.Output
import org.jetbrains.amper.TaskAction
import org.jetbrains.amper.buildinfo.AmperBuild
import org.jetbrains.amper.stdlib.hashing.sha256String
import org.jetbrains.amper.wrapper.AmperWrappers
import java.nio.file.Path
import java.util.zip.GZIPOutputStream
import kotlin.collections.forEach
import kotlin.io.path.createDirectories
import kotlin.io.path.extension
import kotlin.io.path.inputStream
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readBytes

@TaskAction
fun buildDist(
    @Output distributionDir: Path,
    @Output wrappersDir: Path,
    @Input cliRuntimeClasspath: Classpath,
    @Input settings: DistributionSettings,
) {
    val cliTgz = distributionDir.createDirectories().resolve("cli.tgz")
    val extraClasspaths = settings.extraClasspaths

    println("Writing CLI distribution to $cliTgz")
    cliTgz.writeDistTarGz(
        cliRuntimeClasspath = cliRuntimeClasspath.resolvedFiles,
        extraClasspaths = extraClasspaths.mapValues { (_, classpath) -> classpath.resolvedFiles },
    )

    AmperWrappers.generate(
        targetDir = wrappersDir.createDirectories(),
        amperVersion = AmperBuild.mavenVersion,
        amperDistTgzSha256 = cliTgz.readBytes().sha256String(),
    )
}

private fun Path.writeDistTarGz(cliRuntimeClasspath: List<Path>, extraClasspaths: Map<String, List<Path>>) {
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
    // Necessary for ByteBuddy loading the coroutines agent
    "-XX:+EnableDynamicAgentLoading",
    // Smaller memory footprint because each object takes less space, less GC, more memory locality
    "-XX:+UseCompactObjectHeaders",
    // Needed in JRE 24+ because of some JNA usages
    "--enable-native-access=ALL-UNNAMED",
    // Needed in JRE 24+ because of OpenTelemetry (see https://github.com/open-telemetry/opentelemetry-java/issues/7219)
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
