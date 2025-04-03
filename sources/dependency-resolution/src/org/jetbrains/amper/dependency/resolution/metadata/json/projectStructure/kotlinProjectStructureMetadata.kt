/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.json.projectStructure

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import org.jetbrains.amper.dependency.resolution.metadata.json.json

internal fun String.parseKmpLibraryMetadata(): KotlinProjectStructureMetadata = json.decodeFromString(this)

internal fun KotlinProjectStructureMetadata.serialize(): String = json.encodeToString(this)


@Serializable
internal data class KotlinProjectStructureMetadata(
    val projectStructure: ProjectStructure,
)

@Serializable
internal data class ProjectStructure(
    val formatVersion: String,
    val isPublishedAsRoot: String,
    val variants: List<Variant>,
    val sourceSets: List<SourceSet>,
)

@Serializable
internal data class Variant(
    val name: String,
    val sourceSet: List<String>,
)

@Serializable
internal data class SourceSet(
    val name: String,
    val dependsOn: List<String>,
    val moduleDependency: List<String>,
    val sourceSetCInteropMetadataDirectory: String? = null,
    val binaryLayout: String? = null,
    val hostSpecific: String? = null,
)
