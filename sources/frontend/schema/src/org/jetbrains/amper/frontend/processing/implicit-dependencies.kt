/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.frontend.BomDependency
import org.jetbrains.amper.frontend.Fragment
import org.jetbrains.amper.frontend.MavenDependency
import org.jetbrains.amper.frontend.MavenDependencyBase
import org.jetbrains.amper.frontend.ModulePart
import org.jetbrains.amper.frontend.Platform
import org.jetbrains.amper.frontend.RepositoriesModulePart
import org.jetbrains.amper.frontend.ancestralPath
import org.jetbrains.amper.frontend.aomBuilder.DefaultFragment
import org.jetbrains.amper.frontend.aomBuilder.DefaultModule
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableVersion
import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.api.withTraceFrom
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.frontend.schema.legacySerializationFormatNone
import org.jetbrains.amper.frontend.toClassBasedSet

private val kotlinStdlib = kotlinDependencyOf("kotlin-stdlib")
private val kotlinStdlibJdk8 = kotlinDependencyOf("kotlin-stdlib-jdk8")
private val kotlinStdlibJdk7 = kotlinDependencyOf("kotlin-stdlib-jdk7")

private val kotlinTest = kotlinDependencyOf("kotlin-test")
private val kotlinTestAnnotationsCommon = kotlinDependencyOf("kotlin-test-annotations-common")
private val kotlinTestJUnit = kotlinDependencyOf("kotlin-test-junit")
private val kotlinTestJUnit5 = kotlinDependencyOf("kotlin-test-junit5")
private val kotlinTestTestNG = kotlinDependencyOf("kotlin-test-testng")

private val kotlinReflect = kotlinDependencyOf("kotlin-reflect")

private val kotlinParcelizeRuntime = kotlinDependencyOf("kotlin-parcelize-runtime")

private val lombokDependency = MavenDependency(
    coordinates = TraceableString("org.projectlombok:lombok:${UsedVersions.lombokVersion}")
)

private val hotReloadDependency = MavenDependency(
    coordinates = TraceableString("org.jetbrains.compose.hot-reload:runtime-api:${UsedVersions.hotReloadVersion}")
)

private fun kotlinDependencyOf(artifactId: String) = MavenDependency(
    coordinates = library("org.jetbrains.kotlin:$artifactId", UsedVersions.kotlinVersion),
)

// todo (AB) : Why libraries are crated here insteod of being taken from built-in catalogues?
private fun kotlinxSerializationCoreDependency(version: TraceableVersion) = MavenDependency(
    coordinates = library("org.jetbrains.kotlinx:kotlinx-serialization-core", version),
).withTraceFrom(version)

private fun kotlinxSerializationFormatDependency(format: String, version: TraceableString) = MavenDependency(
    coordinates = library("org.jetbrains.kotlinx:kotlinx-serialization-$format", version),
).withTraceFrom(version)

private fun composeRuntimeDependency(composeVersion: TraceableString) = MavenDependency(
    coordinates = library("org.jetbrains.compose.runtime:runtime", composeVersion),
).withTraceFrom(composeVersion)

private fun composeResourcesDependency(composeVersion: TraceableString) = MavenDependency(
    coordinates = library("org.jetbrains.compose.components:components-resources", composeVersion),
).withTraceFrom(composeVersion)

private fun ktorBomDependency(ktorVersion: TraceableString): BomDependency = BomDependency(
    coordinates = library("io.ktor:ktor-bom", ktorVersion),
).withTraceFrom(ktorVersion)

private fun springBootBomDependency(springBootVersion: TraceableString): BomDependency = BomDependency(
    coordinates = library("org.springframework.boot:spring-boot-dependencies", springBootVersion),
).withTraceFrom(springBootVersion)

private fun springBootStarterDependency(springBootVersion: TraceableString): MavenDependency = MavenDependency(
    coordinates = library("org.springframework.boot:spring-boot-starter", springBootVersion),
).withTraceFrom(springBootVersion)

private fun springBootStarterTestDependency(springBootVersion: TraceableString): MavenDependency = MavenDependency(
    coordinates = library("org.springframework.boot:spring-boot-starter-test", springBootVersion),
).withTraceFrom(springBootVersion)


/**
 * Add automatically-added implicit dependencies to default module impl.
 */
internal fun DefaultModule.addImplicitDependencies() {
    fragments.associateWith {
        // Precompute allExternalMavenDependencies because addImplicitDependencies would affect these.
        it.allExternalMavenDependencies().mapTo(hashSetOf()) { it.groupAndArtifact }
    }.forEach { (fragment, deps) -> fragment.addImplicitDependencies(deps) }

    parts = parts.map {
        if (it is RepositoriesModulePart) {
            it.withImplicitMavenRepositories(fragments)
        } else {
            it
        }
    }.toClassBasedSet()
}

private fun Fragment.addImplicitDependencies(
    explicitMavenDependencies: Set<String>,
) {
    val implicitDependencies = calculateImplicitDependencies()
    if (implicitDependencies.isEmpty()) {
        return
    }

    // we don't add an implicit dependency if it is already defined explicitly by the user (in any version)
    val nonOverriddenImplicitDeps = implicitDependencies.filterNot { it.groupAndArtifact in explicitMavenDependencies }

    // TODO report cases where explicit dependencies only partially override a group of related implicit dependencies.
    //   For example, an explicit `kotlin-test` dependency, but no explicit `kotlin-test-junit` dependency (in JVM).
    //   This will require the notion of "groups of implicit dependencies", and we may have to think about transitive
    //   dependencies too: what to do if the junit dependency itself is declared explicitly but not kotlin-test-junit?
    //
    //  Instead of just reporting, should we entirely avoid adding implicit dependencies from the whole group?
    //  Example: the user adds kotlin-test:1.8.20, should we add kotlin-test-junit at all? (the version could mismatch)

    val newExternalDependencies = externalDependencies + nonOverriddenImplicitDeps

    // TODO: Write this in a way that doesn't require mutable list references
    (this as DefaultFragment).externalDependencies = newExternalDependencies
}

private fun Fragment.allExternalMavenDependencies() = ancestralPath()
    .flatMap { it.externalDependencies }
    .filterIsInstance<MavenDependencyBase>()

private fun Fragment.calculateImplicitDependencies(): List<MavenDependencyBase> = buildList {
    add(kotlinStdlib)

    // hack for avoiding classpath clashes in android dependencies, until DR support dependency constraints from
    // Gradle module metadata
    if (platforms == setOf(Platform.ANDROID)) {
        add(kotlinStdlibJdk7)
        add(kotlinStdlibJdk8)
    }

    if (settings.compose.enabled && settings.compose.experimental.hotReload.enabled) {
        add(hotReloadDependency)
    }

    if (isTest) {
        addAll(inferredTestDependencies())
    }
    if (settings.kotlin.serialization.enabled) {
        val kotlinSerializationVersion = TraceableVersion(
            settings.kotlin.serialization.version,
            getSource(
                settings.kotlin.serialization::version.valueBase,
                settings.kotlin.serialization::enabled.valueBase,
            )
        )
        // if kotlinx.serialization plugin is enabled, we need the @Serializable annotation, which is in core
        add(kotlinxSerializationCoreDependency(kotlinSerializationVersion))

        val format = settings.kotlin.serialization.format
        if (format != null && format != legacySerializationFormatNone) {
            add(kotlinxSerializationFormatDependency(format, kotlinSerializationVersion))
        }
    }
    if (settings.android.parcelize.enabled) {
        add(kotlinParcelizeRuntime)
    }
    if (settings.lombok.enabled) {
        add(lombokDependency)
    }
    if (settings.compose.enabled) {
        val composeVersion = TraceableVersion(
            checkNotNull(settings.compose.version),
            getSource(
                settings.compose::version.valueBase,
                settings.compose::enabled.valueBase
            )
        )
        add(composeRuntimeDependency(composeVersion))

        // Have to add dependency because generated code depends on it
        if (settings.compose.resources.exposedAccessors || module.fragments.any { it.hasAnyComposeResources }) {
            add(composeResourcesDependency(composeVersion))
        }
    }

    if (settings.ktor.enabled) {
        val ktorVersion = TraceableVersion(
            checkNotNull(settings.ktor.version),
            getSource(
                settings.ktor::version.valueBase,
                settings.ktor::enabled.valueBase
            ),
        )
        add(ktorBomDependency(ktorVersion))
    }

    if (settings.springBoot.enabled) {
        val springBootVersion = TraceableVersion(
            checkNotNull(settings.springBoot.version),
            getSource(
                settings.springBoot::version.valueBase,
                settings.springBoot::enabled.valueBase
            ),
        )
        add(springBootBomDependency(springBootVersion))
        add(springBootStarterDependency(springBootVersion))
        add(kotlinReflect)
    }
}

private fun getSource(versionValue: ValueBase<*>?, enabledValue: ValueBase<*>?): ValueBase<*>? {
    return if (versionValue?.withoutDefault == null) enabledValue else versionValue
}


private fun Fragment.inferredTestDependencies(): List<MavenDependency> {
    return buildList {
        if (platforms.size == 1 && platforms.single().supportsJvmTestFrameworks()) {
            when (settings.junit) {
                // TODO support kotlin-test-testng?
                //   For this, we should rename settings.junit -> settings.jvm.testFramework and add the TESTNG value to the enum
                JUnitVersion.JUNIT4 -> add(kotlinTestJUnit)
                JUnitVersion.JUNIT5 -> add(kotlinTestJUnit5)
                JUnitVersion.NONE -> add(kotlinTest)
            }
        } else {
            add(kotlinTest)
            add(kotlinTestAnnotationsCommon)
        }

        if (settings.springBoot.enabled) {
            val springBootVersion =
                TraceableVersion(
                    checkNotNull(settings.springBoot.version),
                    getSource(
                        settings.springBoot::version.valueBase,
                        settings.springBoot::enabled.valueBase,
                    )
                )
            add(springBootStarterTestDependency(springBootVersion))
        }
    }
}

private fun Platform.supportsJvmTestFrameworks() = this == Platform.JVM || this == Platform.ANDROID

private val MavenDependencyBase.groupAndArtifact: String
    get() {
        val parts = coordinates.value.split(":", limit = 3)
        // Some tests don't have actual coordinates, maybe in real life we might also not have a group:artifact prefix.
        // This is not the place to fail if we want validation on maven coordinates, so we just go "best effort" here.
        return if (parts.size >= 2) "${parts[0]}:${parts[1]}" else coordinates.value
    }

private fun RepositoriesModulePart.withImplicitMavenRepositories(fragments: List<Fragment>): ModulePart<*> {
    val isHotReloadRuntimeApiPresent = fragments
        .flatMap { it.externalDependencies }
        .filterIsInstance<MavenDependency>()
        .any { it.groupAndArtifact == "org.jetbrains.compose:hot-reload-runtime-api" }
    if (!isHotReloadRuntimeApiPresent) {
        return this
    }
    return RepositoriesModulePart(
        mavenRepositories = mavenRepositories + RepositoriesModulePart.Repository(
            id = "firework-dev",
            url = "https://packages.jetbrains.team/maven/p/firework/dev",
            publish = false,
            resolve = true,
        )
    )
}
