/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.xml

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName
import org.jetbrains.amper.dependency.resolution.AmperDependencyResolutionException

internal fun String.parseMetadata(): Metadata = try {
    xml.decodeFromString(this)
} catch (e: SerializationException) {
    throw AmperDependencyResolutionException("Couldn't parse XML metadata. Invalid content:\n\n${this}", e)
}

internal fun Metadata.serialize(): String = xml.encodeToString(this)

@Serializable
@XmlSerialName("metadata")
internal data class Metadata(
    @XmlElement(true)
    val groupId: String,
    @XmlElement(true)
    val artifactId: String,
    @XmlElement(true)
    val version: String,
    @XmlElement(true)
    val versioning: Versioning
)

@Serializable
@XmlSerialName("versioning")
internal data class Versioning(
    @XmlElement(true)
    val snapshot: Snapshot,
    @XmlElement(true)
    val lastUpdated: String,
    @XmlElement(true)
    val snapshotVersions: SnapshotVersions? = null, // absent when User-Agent is not sent in the request
)

@Serializable
@XmlSerialName("snapshot")
internal data class Snapshot(
    @XmlElement(true)
    val timestamp: String? = null,
    @XmlElement(true)
    val buildNumber: Int? = null,
    @XmlElement(true)
    val localCopy: Boolean? = null,
)

@Serializable
@XmlSerialName("snapshotVersions")
internal data class SnapshotVersions(
    @XmlElement(true)
    val snapshotVersions: List<SnapshotVersion>
)

@Serializable
@XmlSerialName("snapshotVersion")
internal data class SnapshotVersion(
    @XmlElement(true)
    val classifier: String? = null,
    @XmlElement(true)
    val extension: String,
    @XmlElement(true)
    val value: String,
    @XmlElement(true)
    val updated: String
)
