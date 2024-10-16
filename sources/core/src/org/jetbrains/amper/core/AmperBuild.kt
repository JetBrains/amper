/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import java.time.Instant
import java.util.*

object AmperBuild {
    val isSNAPSHOT: Boolean

    /**
     * The current version of Amper as seen in Maven dependencies.
     */
    val mavenVersion: String

    private val commitHash: String?
    private val commitShortHash: String?
    private val buildDate: Instant?
    private val localChangesHash: String?

    /**
     * The Amper product name and version.
     */
    val banner: String
        get() = "JetBrains Amper version $codeIdentifier"

    /**
     * A reliable number representing the state of the Amper code being run.
     *
     * In official CI builds (release or dev), this is the Amper version and the build commit's short hash.
     * In snapshots builds (Amper from sources), this is the Amper version, commit hash, and local changes hash.
     */
    val codeIdentifier: String
        get() {
            val commitHash = commitShortHash?.let { "+$it" } ?: ""
            val localChanges = localChangesHash?.let { "+$it" } ?: ""
            return if (isSNAPSHOT) "$mavenVersion$commitHash$localChanges" else "$mavenVersion$commitHash"
        }

    init {
        this::class.java.getResourceAsStream("/build.properties").use { res ->
            if (res == null) {
                // TODO this is temporary workaround to run Amper built by pure Amper
                // please remove this hack when build.properties will be generated by pure Amper backend too

                isSNAPSHOT = true
                mavenVersion = "1.0-SNAPSHOT"
                commitHash = null
                commitShortHash = null
                buildDate = null
                localChangesHash = null

                return@use
            }

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
            localChangesHash = props.getProperty("localChangesHash")
        }
    }
}
