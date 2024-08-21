/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.android

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import java.util.Properties
import kotlin.io.path.Path
import kotlin.io.path.absolute
import kotlin.io.path.readText
import kotlin.io.path.div

val json = Json { ignoreUnknownKeys = true }

interface LazyArtifact {
    val value: Path
}

data class DirectLazyArtifact(override val value: Path) : LazyArtifact

@Serializable
data class OutputMetadata(val elements: List<Element>) {
    @Serializable
    data class Element(val outputFile: String)
}

class RedirectedLazyArtifact(private val redirectFile: Path) : LazyArtifact {
    override val value: Path
        get() {
            val properties = Properties()
            redirectFile.toFile().inputStream().use { properties.load(it) }
            val listingFile = properties.getProperty("listingFile")?.let { Path(it) } ?: error("Listing file not found")
            val outputMetadataJsonFile = redirectFile.parent / listingFile
            val content = outputMetadataJsonFile.readText()
            return outputMetadataJsonFile
                .parent
                .resolve(
                    json
                        .decodeFromString<OutputMetadata>(content)
                        .elements
                        .first()
                        .outputFile
                )
                .normalize()
                .absolute()
        }
}

fun redirect(file: File): RedirectedLazyArtifact = RedirectedLazyArtifact(file.toPath())
