/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

/**
 * Versions used within project.
 * This file should be changed automatically by `syncVersions.sh`.
 * Use /*Replacement*/ to help script finding versions.
 */
object UsedVersions {

    /*magic_replacement*/ val kotlinVersion = "2.0.0-RC1"

    /*magic_replacement*/ val androidVersion = "8.2.2"

    /*magic_replacement*/ val composeVersion = "1.6.2"

    // No replacement yet.
    val junit4Version = "4.12"

    // No replacement yet.
    val junit5Version = "5.9.2"

    /**
     * Returns the Compose (multiplatform) compiler plugin version matching the given [kotlinVersion].
     * 
     * **IMPORTANT:** The user-defined Compose version is only for runtime libraries and the Compose Gradle plugin.
     * The Compose compiler has a different versioning scheme, with a mapping to the Kotlin compiler versions.
     *
     * **How to update this list**
     *
     * The main table in the official documentation table lists the Compose Gradle plugin (and runtime library) version.
     * We have to look at [the dev versions table](https://www.jetbrains.com/help/kotlin-multiplatform-dev/compose-compatibility-and-versioning.html#use-a-developer-version-of-compose-multiplatform-compiler)
     * to get information about the Compose compiler plugin itself.
     *
     * Look at the [mapping in the Gradle plugin](https://github.com/JetBrains/compose-multiplatform/blob/master/gradle-plugins/compose/src/main/kotlin/org/jetbrains/compose/ComposeCompilerCompatibility.kt)
     * for the most up-to-date reference.
     */
    fun composeCompilerVersionFor(kotlinVersion: String): String? = when (kotlinVersion) {
        "1.7.10" -> "1.3.0"
        "1.7.20" -> "1.3.2.2"
        "1.8.0" -> "1.4.0"
        "1.8.10" -> "1.4.2"
        "1.8.20" -> "1.4.5"
        "1.8.21" -> "1.4.7"
        "1.8.22" -> "1.4.8"
        "1.9.0-Beta" -> "1.4.7.1-beta"
        "1.9.0-RC" -> "1.4.8-beta"
        "1.9.0" -> "1.5.1"
        "1.9.10" -> "1.5.2"
        "1.9.20-Beta" -> "1.5.2.1-Beta2"
        "1.9.20-Beta2" -> "1.5.2.1-Beta3"
        "1.9.20-RC" -> "1.5.2.1-rc01"
        "1.9.20-RC2" -> "1.5.3-rc01"
        "1.9.20" -> "1.5.3"
        "1.9.21" -> "1.5.4"
        "1.9.22" -> "1.5.8.1"
        "2.0.0-Beta1" -> "1.5.4-dev1-kt2.0.0-Beta1"
        "2.0.0-Beta4" -> "1.5.9-kt-2.0.0-Beta4"
        "2.0.0-RC1" -> "1.5.11-kt-2.0.0-RC1"
        else -> null // the error will be handled differently depending on the caller
    }
}
