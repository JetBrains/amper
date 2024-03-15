/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jar

import kotlinx.serialization.Serializable
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes
import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createParentDirectories
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream
import kotlin.io.path.pathString
import kotlin.io.path.readAttributes
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

// TODO support signing?
// TODO support for Zip64 to allow many many classes?
@Serializable
data class JarConfig(
    /**
     * The fully qualified name of a class to specify as Main-Class attribute in the jar manifest.
     * This is necessary to create executable jars.
     */
    val mainClassFqn: String? = null,
    /**
     * Ensures the files are written to the jar in an consistent order that's independent of the file system or OS,
     * typically by sorting the file paths 
     * This must be enabled in order to get reproducible jars.
     */
    val reproducibleFileOrder: Boolean = true,
    /**
     * Copies the original file timestamps (creation, last access, last modification) to the jar entries.
     * This must be disabled in order to get reproducible jars.
     */
    val preserveFileTimestamps: Boolean = false,
)

/**
 * Creates a jar file at this [Path] and writes all files from the given [inputDirs] into that jar.
 * Parts of this process can be configured using the given [config].
 */
fun Path.writeJar(inputDirs: List<Path>, config: JarConfig) {
    val manifest = createManifest(config)
    JarOutputStream(outputStream(), manifest).use { out ->
        inputDirs.forEach { dir ->
            out.writeDirContents(dir, config)
        }
    }
}

private fun createManifest(config: JarConfig): Manifest = Manifest().apply {
    mainAttributes[Attributes.Name.MANIFEST_VERSION] = "1.0"
    if (config.mainClassFqn != null) {
        mainAttributes[Attributes.Name.MAIN_CLASS] = config.mainClassFqn
    }
}

/**
 * Writes all files from the given [directory] (recursively) to this [JarOutputStream].
 * The path of the files within the jar is their original path relative to [directory].
 */
@OptIn(ExperimentalPathApi::class)
private fun JarOutputStream.writeDirContents(directory: Path, config: JarConfig) {
    directory.walk().sortedIf(config.reproducibleFileOrder).forEach {
        val entryName = it.relativeTo(directory).joinToString("/") // ensures / is used even on Windows
        writeJarEntry(entryName = entryName, file = it, config)
    }
}

// sorting the Paths themselves is not consistent across OS-es, we need to use the pathStrings
private fun Sequence<Path>.sortedIf(condition: Boolean): Sequence<Path> =
    if (condition) sortedBy { it.pathString } else this

/**
 * Writes the contents of the given [file] as a new jar entry called [entryName].
 */
private fun JarOutputStream.writeJarEntry(entryName: String, file: Path, config: JarConfig) {
    val jarEntry = JarEntry(entryName)
    if (config.preserveFileTimestamps) {
        val fileAttributes = file.readAttributes<BasicFileAttributes>()
        jarEntry.creationTime = fileAttributes.creationTime()
        jarEntry.lastAccessTime = fileAttributes.lastAccessTime()
        jarEntry.lastModifiedTime = fileAttributes.lastModifiedTime()
    }
    putNextEntry(jarEntry)
    file.inputStream().use {
        it.copyTo(this)
    }
    closeEntry()
}
