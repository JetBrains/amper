/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.Module

context(SystemInfo)
fun Module.replaceComposeOsSpecific() = apply {
    fun List<Dependency>.replaceComposeOsSpecific() = mapNotNull {
        if (it !is ExternalMavenDependency) return@mapNotNull it
        else replaceComposeOsSecific(it)
    }

    fun Map<Modifiers, List<Dependency>>.replaceComposeOsSpecific() =
        entries.associate { it.key to it.value.replaceComposeOsSpecific() }

    // Actual replacement.
    dependencies(dependencies.value.replaceComposeOsSpecific())
    `test-dependencies`(`test-dependencies`.value.replaceComposeOsSpecific())
}

context(SystemInfo)
fun replaceComposeOsSecific(other: ExternalMavenDependency) = when {
    other.coordinates.value.startsWith("org.jetbrains.compose.desktop:desktop-jvm:") ->
        ExternalMavenDependency().apply {
            coordinates(
                other.coordinates.value.replace(
                    "org.jetbrains.compose.desktop:desktop-jvm",
                    "org.jetbrains.compose.desktop:desktop-jvm-${detect().familyArch}"
                )
            )
            scope(other.scope.value)
            exported(other.exported.value)
        }

    other.coordinates.value.startsWith("org.jetbrains.compose.desktop:desktop:") ->
        ExternalMavenDependency().apply {
            coordinates(
                other.coordinates.value.replace(
                    "org.jetbrains.compose.desktop:desktop",
                    "org.jetbrains.compose.desktop:desktop-jvm-${detect().familyArch}"
                )
            )
            scope(other.scope.value)
            exported(other.exported.value)
        }

    else -> other
}
