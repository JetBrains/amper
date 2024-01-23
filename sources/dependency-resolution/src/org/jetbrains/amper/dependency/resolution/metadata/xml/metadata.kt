/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.metadata.xml

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import nl.adaptivity.xmlutil.serialization.XmlElement
import nl.adaptivity.xmlutil.serialization.XmlSerialName

internal fun String.parseMetadata(): Metadata = xml.decodeFromString(this)

internal fun Metadata.serialize(): String = xml.encodeToString(this)

@Serializable
@XmlSerialName("metadata")
data class Metadata(
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
data class Versioning(
    @XmlElement(true)
    val snapshot: Snapshot,
    @XmlElement(true)
    val lastUpdated: String,
    @XmlElement(true)
    val snapshotVersions: SnapshotVersions
)

@Serializable
@XmlSerialName("snapshot")
data class Snapshot(
    @XmlElement(true)
    val timestamp: String,
    @XmlElement(true)
    val buildNumber: Int
)

@Serializable
@XmlSerialName("snapshotVersions")
data class SnapshotVersions(
    @XmlElement(true)
    val snapshotVersions: List<SnapshotVersion>
)

@Serializable
@XmlSerialName("snapshotVersion")
data class SnapshotVersion(
    @XmlElement(true)
    val classifier: String? = null,
    @XmlElement(true)
    val extension: String,
    @XmlElement(true)
    val value: String,
    @XmlElement(true)
    val updated: String
)
