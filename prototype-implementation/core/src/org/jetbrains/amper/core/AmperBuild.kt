/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

import java.time.Instant
import java.util.*

object AmperBuild {
    val isSNAPSHOT: Boolean
    val BuildNumber: String
    val CommitHash: String?
    val BuildDate: Instant?

    init {
        this::class.java.getResourceAsStream("/build.properties").use { res ->
            val props = Properties()
            props.load(res)

            fun getProperty(property: String): String {
                val value: String? = props.getProperty(property)
                if (value.isNullOrBlank()) {
                    throw IllegalStateException("Property '$property' is not present in build.properties")
                }
                return value
            }

            BuildNumber = getProperty("version")
            isSNAPSHOT = BuildNumber.contains("-SNAPSHOT")
            CommitHash = props.getProperty("commitHash")
            BuildDate = props.getProperty("commitDate")?.let { Instant.parse(it) }
        }
    }
}
