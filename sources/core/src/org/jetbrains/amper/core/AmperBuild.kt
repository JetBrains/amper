/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.inputStream

object AmperBuild {
    val isSNAPSHOT: Boolean

    /**
     * The current version of Amper as seen in Maven dependencies.
     */
    val mavenVersion: String

    private val commitHash: String?
    private val commitShortHash: String?
    private val buildDate: Instant?
    private val distributionHash: String?

    /**
     * The Amper product name and version.
     */
    val banner: String
        get() = "JetBrains Amper version $codeIdentifier"

    /**
     * A reliable number representing the state of the Amper code being run.
     *
     * In official CI builds (release or dev), this is the Amper version and the build commit's short hash.
     * In snapshots builds (Amper from sources), this is the Amper version, commit hash, and distribution hash.
     */
    val codeIdentifier: String
        get() {
            val commitHash = commitShortHash?.let { "+$it" } ?: ""
            val localChanges = distributionHash?.let { "+$it" } ?: ""
            return if (isSNAPSHOT) "$mavenVersion$commitHash$localChanges" else "$mavenVersion$commitHash"
        }

    init {
        this::class.java.getResourceAsStream("/build.properties").use { res ->
            check(res != null) { "Amper was built without build.properties" }

            val props = Properties()
            props.load(res)

            fun getProperty(property: String): String {
                val value: String? = props.getProperty(property)
                if (value.isNullOrBlank()) {
                    error("Property '$property' is not present in build.properties")
                }
                return value
            }

            mavenVersion = getProperty("version")
            isSNAPSHOT = mavenVersion.contains("-SNAPSHOT")
            commitHash = props.getProperty("commitHash")
            commitShortHash = props.getProperty("commitShortHash")
            buildDate = props.getProperty("commitDate")?.let { Instant.parse(it) }

            // The incremental cache always invalidates any state that was produced by a different Amper version.
            // When developing locally, the version is always 1.0-SNAPSHOT, so this mechanism isn't triggered.
            // This could cause issues because some tasks will be marked up-to-date even though their code has changed
            // (and thus they might produce a different output). This is why, in the case of a snapshot, we also
            // consider the hash of the running distribution jars as part of the version.
            distributionHash = if (isSNAPSHOT) computeDistributionHash() else null
        }
    }
}

private fun computeDistributionHash(): String? {
    val classPath = System.getProperty("java.class.path").ifEmpty { null } ?: return null
    val classPathFiles = classPath.split(File.pathSeparator).map { Path(it) }
    return md5All(classPathFiles)
}

@OptIn(ExperimentalStdlibApi::class)
private fun md5All(classPathFiles: List<Path>): String {
    val hasher = MessageDigest.getInstance("md5")
    classPathFiles.forEach { file ->
        hasher.update(file)
    }
    return hasher.digest().toHexString()
}

private fun MessageDigest.update(file: Path) {
    file.inputStream().use { update(it) }
}

private fun MessageDigest.update(data: InputStream) {
    val buffer = ByteArray(1024)
    var read = data.read(buffer)
    while (read > -1) {
        update(buffer, 0, read)
        read = data.read(buffer)
    }
}
