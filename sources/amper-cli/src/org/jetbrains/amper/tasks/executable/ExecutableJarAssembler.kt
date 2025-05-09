/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks.executable

import org.jetbrains.amper.core.AmperUserCacheRoot
import org.jetbrains.amper.core.extract.extractFileToCacheLocation
import org.jetbrains.amper.incrementalcache.ExecuteOnChangedInputs
import org.jetbrains.amper.jar.JarConfig
import org.jetbrains.amper.jar.ZipInput
import org.jetbrains.amper.jar.CompressionStrategy
import org.jetbrains.amper.jar.ZipConfig
import org.jetbrains.amper.run.ToolingArtifactsDownloader
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.createTempFile
import kotlin.io.path.writeText
import kotlin.io.path.div

/**
 * Assembles an executable JAR with the appropriate structure and configuration.
 */
class ExecutableJarAssembler(
    private val userCacheRoot: AmperUserCacheRoot,
    private val executeOnChangedInputs: ExecuteOnChangedInputs
) {

    suspend fun ExecutableJarConfig.prepareJarInputs(
        classes: List<Path>,
        runtimeDependencies: List<Path>
    ): List<ZipInput> {
        // Map classes to the BOOT-INF/classes directory
        val classesInputs = classes.map { ZipInput(it, Path("BOOT-INF/classes")) }

        // Map runtime dependencies to the BOOT-INF/lib directory
        val libInputs = runtimeDependencies.map { ZipInput(it, libDirectory / it.fileName) }

        // Download and extract Spring Boot loader
        val loaderJar = downloadSpringBootLoader()
        val extractedJar = extractFileToCacheLocation(loaderJar, userCacheRoot)
        val loaderInput = ZipInput(extractedJar, Path("."))

        // Create classpath index
        val classpathIndexInput = createClasspathIndex(libInputs)

        // Create layers index
        val layersIndexInput = createLayersIndex()

        // Combine all inputs
        return classesInputs + libInputs + listOf(loaderInput, classpathIndexInput, layersIndexInput)
    }

    fun ExecutableJarConfig.createJarConfig(): JarConfig {
        return JarConfig(
            mainClassFqn = loaderMainClass,
            manifestProperties = convertToManifestProperties(),
            zipConfig = ZipConfig(
                compressionStrategy = CompressionStrategy.Selective,
                uncompressedEntryPatterns = listOf("^BOOT-INF/lib/.+"),
            ),
        )
    }

    private suspend fun downloadSpringBootLoader(): Path {
        val toolingArtifactsDownloader = ToolingArtifactsDownloader(userCacheRoot, executeOnChangedInputs)
        return toolingArtifactsDownloader.downloadSpringBootLoader()
    }

    private fun ExecutableJarConfig.createClasspathIndex(libInputs: List<ZipInput>): ZipInput {
        val classpathIndex = libInputs.map { it.destPathInArchive.toString() }
        val classpathIndexFile = createTempFile("classpath", ".idx")

        classpathIndexFile.writeText(buildString {
            classpathIndex.forEach {
                append("- \"$it\"\n")
            }
        })

        return ZipInput(classpathIndexFile, classpathIndexPath)
    }

    private fun ExecutableJarConfig.createLayersIndex(): ZipInput {
        val layersFile = createTempFile("layers", ".idx")

        layersFile.writeText(
            """
            - "dependencies":
              - "$libDirectory"
            - "spring-boot-loader":
              - "org/"
            - "snapshot-dependencies":
            - "application":
              - "$classesDirectory"
              - "$classpathIndexPath"
              - "$layersIndexPath"
              - "META-INF/"
            """.trimIndent()
        )

        return ZipInput(layersFile, layersIndexPath)
    }
}