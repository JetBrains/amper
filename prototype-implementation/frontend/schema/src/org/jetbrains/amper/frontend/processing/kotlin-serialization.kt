/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module

fun Module.addKotlinSerialization() = apply {
    fun List<Dependency>.addKotlinSerialization() = run {
        if (any { it.isKotlinSerialization }) this
        else this + jsonKotlinSerialization
    }

    // Add dependency for common fragment.
    fun Map<Modifiers, List<Dependency>>.addKotlinSerialization() =
        toMutableMap().apply {
            compute(emptySet()) { modifiers, v ->
                if (settings.value[modifiers]?.kotlin?.value?.serialization?.value?.engine?.value == "json")
                    v.orEmpty().addKotlinSerialization()
                else v
            }
        }

    // Actual replacement.
    dependencies(dependencies.value.addKotlinSerialization())
    `test-dependencies`(`test-dependencies`.value.addKotlinSerialization())
}

const val jsonKotlinSerializationCoordinates = "org.jetbrains.kotlinx:kotlinx-serialization-json"
const val jsonKotlinSerializationVersion = "1.5.1"
val Dependency.isKotlinSerialization
    get() =
        (this as? ExternalMavenDependency)?.coordinates?.value?.startsWith(jsonKotlinSerializationCoordinates) == true
val jsonKotlinSerialization = ExternalMavenDependency().apply {
    coordinates("$jsonKotlinSerializationCoordinates:$jsonKotlinSerializationVersion")
}