/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.buildinfo

import java.time.Instant
import java.util.*

object AmperBuild {
    val isSNAPSHOT: Boolean

    /**
     * The current version of Amper as seen in Maven dependencies.
     */
    val mavenVersion: String

    val commitHash: String?
    val commitShortHash: String?
    val commitInstant: Instant?

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
            commitInstant = props.getProperty("commitDate")?.let { Instant.parse(it) }
        }
    }
}