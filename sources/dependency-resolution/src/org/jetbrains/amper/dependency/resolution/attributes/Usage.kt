/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.dependency.resolution.attributes

sealed class Usage(override val value: String) : AttributeValue {
    companion object : Attribute<Usage> {
        override val name: String = "org.gradle.usage"

        override fun fromString(value: String): Usage = when (value) {
            KotlinApi.value -> KotlinApi
            KotlinMetadata.value -> KotlinMetadata
            else -> Other(value)
        }
    }

    object KotlinApi : Usage("kotlin-api")
    object KotlinMetadata : Usage("kotlin-metadata")
    class Other(value: String) : Usage(value)

    fun isApi(): Boolean = value.endsWith("-api")
    fun isRuntime(): Boolean = value.endsWith("-runtime")

    fun isComposeDevJavaRuntime(): Boolean = value == "compose-dev-java-runtime"
}
