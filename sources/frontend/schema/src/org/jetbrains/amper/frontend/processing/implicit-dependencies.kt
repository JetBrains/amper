/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.LeafFragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.ancestralPath
import org.jetbrains.amper.frontend.aomBuilder.DefaultModule
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.frontend.schema.legacySerializationFormatNone

private val kotlinStdlib = kotlinDependencyOf("kotlin-stdlib")
private val kotlinStdlibJdk8 = kotlinDependencyOf("kotlin-stdlib-jdk8")
private val kotlinStdlibJdk7 = kotlinDependencyOf("kotlin-stdlib-jdk7")

private val kotlinTest = kotlinDependencyOf("kotlin-test")
private val kotlinTestAnnotationsCommon = kotlinDependencyOf("kotlin-test-annotations-common")
private val kotlinTestJUnit = kotlinDependencyOf("kotlin-test-junit")
private val kotlinTestJUnit5 = kotlinDependencyOf("kotlin-test-junit5")
private val kotlinTestTestNG = kotlinDependencyOf("kotlin-test-testng")

private val kotlinParcelizeRuntime = kotlinDependencyOf("kotlin-parcelize-runtime")

private fun kotlinDependencyOf(artifactId: String) = MavenDependency(
    coordinates = "org.jetbrains.kotlin:$artifactId:${UsedVersions.kotlinVersion}",
)

private fun kotlinxSerializationCoreDependency(version: String) = MavenDependency(
    coordinates = "org.jetbrains.kotlinx:kotlinx-serialization-core:$version",
)

private fun kotlinxSerializationFormatDependency(format: String, version: String) = MavenDependency(
    coordinates = "org.jetbrains.kotlinx:kotlinx-serialization-$format:$version",
)

private fun composeRuntimeDependency(composeVersion: String) = MavenDependency(
    coordinates = "org.jetbrains.compose.runtime:runtime:$composeVersion",
)

private fun composeResourcesDependency(composeVersion: String) = MavenDependency(
    coordinates = "org.jetbrains.compose.components:components-resources:$composeVersion",
)

/**
 * Add automatically-added implicit dependencies to default module impl.
 */
internal fun DefaultModule.addImplicitDependencies() {
    fragments = fragments.map { it.withImplicitDependencies() }
}

private fun Fragment.withImplicitDependencies(): Fragment {
    val implicitDependencies = calculateImplicitDependencies()
    if (implicitDependencies.isEmpty()) {
        return this
    }

    // we don't add an implicit dependency if it is already defined explicitly by the user (in any version)
    val explicitMavenDependencies = allExternalMavenDependencies().map { it.groupAndArtifact }.toSet()
    val nonOverriddenImplicitDeps = implicitDependencies.filterNot { it.groupAndArtifact in explicitMavenDependencies }

    // TODO report cases where explicit dependencies only partially override a group of related implicit dependencies.
    //   For example, an explicit `kotlin-test` dependency, but no explicit `kotlin-test-junit` dependency (in JVM).
    //   This will require the notion of "groups of implicit dependencies", and we may have to think about transitive
    //   dependencies too: what to do if the junit dependency itself is declared explicitly but not kotlin-test-junit?
    //
    //  Instead of just reporting, should we entirely avoid adding implicit dependencies from the whole group?
    //  Example: the user adds kotlin-test:1.8.20, should we add kotlin-test-junit at all? (the version could mismatch)

    val newExternalDependencies = externalDependencies + nonOverriddenImplicitDeps
    return when (this) {
        is LeafFragment -> object : LeafFragment by this {
            override val externalDependencies = newExternalDependencies
            override fun equals(other: Any?) = other != null && other is LeafFragment && name == other.name
            override fun hashCode() = name.hashCode()
        }
        else -> object : Fragment by this {
            override val externalDependencies = newExternalDependencies
            override fun equals(other: Any?) = other != null && other is LeafFragment && name == other.name
            override fun hashCode() = name.hashCode()
        }
    }
}

private fun Fragment.allExternalMavenDependencies() = ancestralPath()
    .flatMap { it.externalDependencies }
    .filterIsInstance<MavenDependency>()

private fun Fragment.calculateImplicitDependencies(): List<MavenDependency> = buildList {
    add(kotlinStdlib)

    // hack for avoiding classpath clashes in android dependencies, until DR support dependency constraints from
    // Gradle module metadata
    if (platforms == setOf(Platform.ANDROID)) {
        add(kotlinStdlibJdk7)
        add(kotlinStdlibJdk8)
    }

    if (isTest) {
        addAll(inferredTestDependencies())
    }
    if (settings.kotlin.serialization.enabled) {
        // if kotlinx.serialization plugin is enabled, we need the @Serializable annotation, which is in core
        add(kotlinxSerializationCoreDependency(settings.kotlin.serialization.version))

        val format = settings.kotlin.serialization.format
        if (format != null && format != legacySerializationFormatNone) {
            add(kotlinxSerializationFormatDependency(format, settings.kotlin.serialization.version))
        }
    }
    if (settings.android.parcelize.enabled) {
        add(kotlinParcelizeRuntime)
    }
    if (settings.compose.enabled) {
        val composeVersion = checkNotNull(settings.compose.version)
        add(composeRuntimeDependency(composeVersion))

        if (hasAnyComposeResources) {
            add(composeResourcesDependency(composeVersion))
        }
    }
}

private fun Fragment.inferredTestDependencies(): List<MavenDependency> =
    if (platforms.size == 1 && platforms.single().supportsJvmTestFrameworks()) {
        when (settings.junit) {
            // TODO support kotlin-test-testng?
            //   For this, we should rename settings.junit -> settings.jvm.testFramework and add the TESTNG value to the enum
            JUnitVersion.JUNIT4 -> listOf(kotlinTestJUnit)
            JUnitVersion.JUNIT5 -> listOf(kotlinTestJUnit5)
            JUnitVersion.NONE -> listOf(kotlinTest)
        }
    } else {
        listOf(kotlinTest, kotlinTestAnnotationsCommon)
    }

private fun Platform.supportsJvmTestFrameworks() = this == Platform.JVM || this == Platform.ANDROID

private val MavenDependency.groupAndArtifact: String
    get() {
        val parts = coordinates.split(":", limit = 3)
        // Some tests don't have actual coordinates, maybe in real life we might also not have a group:artifact prefix.
        // This is not the place to fail if we want validation on maven coordinates, so we just go "best effort" here.
        return if (parts.size >= 2) "${parts[0]}:${parts[1]}" else coordinates
    }
