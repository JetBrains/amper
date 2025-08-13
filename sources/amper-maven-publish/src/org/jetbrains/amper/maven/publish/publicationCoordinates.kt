/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.maven.publish

import org.jetbrains.amper.dependency.resolution.MavenCoordinates
import org.jetbrains.amper.frontend.AmperModule
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.Platform

fun AmperModule.publicationCoordinates(platform: Platform): MavenCoordinates = when {
    // for JVM-only libraries, we use the root publication format (without -jvm suffix)
    platform == Platform.COMMON || platform == Platform.JVM && isJvmOnly -> rootPublicationCoordinates()
    platform.isLeaf -> kmpLeafPlatformPublicationCoordinates(platform)
    else -> error("Cannot generate Maven coordinates for $platform: only COMMON and leaf platforms are supported")
}

private val AmperModule.isJvmOnly get() = leafPlatforms == setOf(Platform.JVM)

private fun AmperModule.rootPublicationCoordinates(): MavenCoordinates {
    val commonFragment = fragments.find { !it.isTest && it.fragmentDependencies.isEmpty() }
        ?: error("Cannot generate root Maven coordinates for module '$userReadableName': no root fragment")

    return commonFragment.mavenCoordinates(artifactIdSuffix = "")
}

private fun AmperModule.kmpLeafPlatformPublicationCoordinates(platform: Platform): MavenCoordinates {
    val fragment = leafFragments.singleOrNull { !it.isTest && platform in it.platforms }
        ?: error("Cannot generate Maven coordinates for module '$userReadableName' with platform $platform: expected " +
                "a single leaf fragment supporting this platform, but got " +
                "${leafFragments.filter { platform in it.platforms }.map { it.name }}")

    // the leaf fragment should inherit publication settings from parents, no need to browse again
    return fragment.mavenCoordinates(artifactIdSuffix = "-${platform.schemaValue.lowercase()}")
}

private fun Fragment.mavenCoordinates(artifactIdSuffix: String): MavenCoordinates {
    val publishSettings = settings.publishing
        ?: error("No publishing settings in fragment '$name' of module '${module.userReadableName}'")

    val artifactId = (publishSettings.name ?: module.userReadableName.lowercase()) + artifactIdSuffix
    val groupId = publishSettings.group ?: error("Missing 'group' in publishing settings of fragment '$name' of module '${module.userReadableName}'")
    val version = publishSettings.version ?: error("Missing 'version' in publishing settings of fragment '$name' of module '${module.userReadableName}'")
    return MavenCoordinates(groupId, artifactId, version)
}
