/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.buildinfo.AmperBuild
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
import org.jetbrains.amper.frontend.api.SchemaValueDelegate
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.Trace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TransformedValueTrace
import org.jetbrains.amper.frontend.api.isExplicitlySet
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.schema.JUnitVersion
import org.jetbrains.amper.frontend.schema.ProductType
import org.jetbrains.amper.frontend.schema.Repository.Companion.SpecialMavenLocalUrl
import org.jetbrains.amper.frontend.schema.legacySerializationFormatNone
import org.jetbrains.amper.frontend.toClassBasedSet

private fun kotlinDependencyOf(artifactId: String, version: TraceableString, dependencyTrace: Trace) = MavenDependency(
    coordinates = coords("org.jetbrains.kotlin:$artifactId", version),
    trace = dependencyTrace,
)

private fun lombokDependency(version: TraceableString, dependencyTrace: Trace) = MavenDependency(
    coordinates = coords("org.projectlombok:lombok", version),
    trace = dependencyTrace,
)

private fun hotReloadDependency(version: TraceableString, dependencyTrace: Trace) = MavenDependency(
    coordinates = coords("org.jetbrains.compose.hot-reload:hot-reload-runtime-api", version),
    trace = dependencyTrace,
)

private fun kotlinxSerializationCoreDependency(version: TraceableString, dependencyTrace: Trace) = MavenDependency(
    coordinates = coords("org.jetbrains.kotlinx:kotlinx-serialization-core", version),
    trace = dependencyTrace,
)

private fun kotlinxSerializationFormatDependency(format: String, version: TraceableString, dependencyTrace: Trace) =
    MavenDependency(
        coordinates = coords("org.jetbrains.kotlinx:kotlinx-serialization-$format", version),
        trace = dependencyTrace,
    )

private fun composeRuntimeDependency(composeVersion: TraceableString, dependencyTrace: Trace) = MavenDependency(
    coordinates = coords("org.jetbrains.compose.runtime:runtime", composeVersion),
    trace = dependencyTrace,
)

private fun composeResourcesDependency(composeVersion: TraceableString, dependencyTrace: Trace) = MavenDependency(
    coordinates = coords("org.jetbrains.compose.components:components-resources", composeVersion),
    trace = dependencyTrace,
)

private fun ktorBomDependency(ktorVersion: TraceableString, dependencyTrace: Trace): BomDependency = BomDependency(
    coordinates = coords("io.ktor:ktor-bom", ktorVersion),
    trace = dependencyTrace,
)

private fun springBootBomDependency(springBootVersion: TraceableString, dependencyTrace: Trace): BomDependency =
    BomDependency(
        coordinates = coords("org.springframework.boot:spring-boot-dependencies", springBootVersion),
        trace = dependencyTrace,
    )

/**
 * Creates a new [TraceableString] with a value computed from this [TraceableString]'s value.
 * The new trace will be the same as this one.
 */
private fun coords(groupAndArtifact: String, version: TraceableString) = TraceableString(
    value = "$groupAndArtifact:${version.value}",
    trace = TransformedValueTrace(
        description = "injected version ${version.value} into default coordinates",
        sourceValue = version,
    ),
)

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
            it.withImplicitMavenRepositories(productType = this.type, fragments)
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
    val kotlinVersion = settings.kotlin::version.schemaDelegate.asTraceableString()
    add(kotlinDependencyOf("kotlin-stdlib", kotlinVersion, DefaultTrace))

    // hack for avoiding classpath clashes in android dependencies, until DR support dependency constraints from
    // Gradle module metadata
    if (platforms == setOf(Platform.ANDROID)) {
        // TODO we should trace to the android platform declaration, but we don't have access to the declared platforms
        //  traces here in the AOM.
        add(kotlinDependencyOf("kotlin-stdlib-jdk7", kotlinVersion, DefaultTrace))
        add(kotlinDependencyOf("kotlin-stdlib-jdk8", kotlinVersion, DefaultTrace))
    }

    if (settings.compose.enabled && settings.compose.experimental.hotReload.enabled) {
        val hotReloadVersion = settings.compose.experimental.hotReload::version.schemaDelegate.asTraceableString()
        val hotReloadEnabledTrace = TransformedValueTrace(
            description = "because Compose Hot Reload is enabled",
            sourceValue = settings.compose.experimental.hotReload::enabled.schemaDelegate,
        )
        add(hotReloadDependency(hotReloadVersion, hotReloadEnabledTrace))
    }

    if (isTest) {
        addAll(inferredTestDependencies())
    }
    if (settings.kotlin.serialization.enabled) {
        val kotlinSerializationVersion = settings.kotlin.serialization::version.schemaDelegate.asTraceableString()
        val serializationEnabledTrace = TransformedValueTrace(
            description = "because Kotlinx Serialization is enabled",
            sourceValue = settings.kotlin.serialization::enabled.schemaDelegate,
        )

        // if kotlinx.serialization plugin is enabled, we need the @Serializable annotation, which is in core
        add(kotlinxSerializationCoreDependency(kotlinSerializationVersion, dependencyTrace = serializationEnabledTrace))

        val format = settings.kotlin.serialization.format
        if (format != null && format != legacySerializationFormatNone) {
            val serializationFormatTrace = TransformedValueTrace(
                description = "because kotlin.serialization.format=$format",
                sourceValue = settings.kotlin.serialization::format.schemaDelegate,
            )
            add(kotlinxSerializationFormatDependency(
                format = format,
                version = kotlinSerializationVersion,
                dependencyTrace = serializationFormatTrace,
            ))
        }
    }
    if (settings.android.parcelize.enabled) {
        // FIXME for some reason using the proper trace instead of DefaultTrace breaks a ParcelizeTest
//        val parcelizeDependencyTrace = TransformedValueTrace(
//            description = "because Android Parcelize is enabled",
//            sourceValue = settings.android.parcelize::enabled.schemaDelegate,
//        )
        add(kotlinDependencyOf("kotlin-parcelize-runtime", kotlinVersion, dependencyTrace = DefaultTrace))
    }
    if (settings.lombok.enabled) {
        val lombokVersion = settings.lombok::version.schemaDelegate.asTraceableString()
        val lombokDependencyTrace = TransformedValueTrace(
            description = "because Lombok is enabled",
            sourceValue = settings.lombok::enabled.schemaDelegate,
        )
        add(lombokDependency(lombokVersion, lombokDependencyTrace))
    }
    if (settings.compose.enabled) {
        val composeVersion = settings.compose::version.schemaDelegate.asTraceableString()
        val composeDependencyTrace = TransformedValueTrace(
            description = "because Compose is enabled",
            sourceValue = settings.compose::enabled.schemaDelegate,
        )
        add(composeRuntimeDependency(composeVersion, dependencyTrace = composeDependencyTrace))

        // Have to add dependency because generated code depends on it
        if (settings.compose.resources.exposedAccessors || module.fragments.any { it.hasAnyComposeResources }) {
            add(composeResourcesDependency(composeVersion, dependencyTrace = composeDependencyTrace))
        }
    }

    if (settings.ktor.enabled && settings.ktor.applyBom) {
        val ktorVersion = settings.ktor::version.schemaDelegate.asTraceableString()
        val ktorApplyBomTrace = TransformedValueTrace(
            description = "because Ktor is enabled and ktor.applyBom=true",
            sourceValue = if (settings.ktor::applyBom.isExplicitlySet) {
                settings.ktor::applyBom.schemaDelegate
            } else {
                settings.ktor::enabled.schemaDelegate
            },
        )
        add(ktorBomDependency(ktorVersion, dependencyTrace = ktorApplyBomTrace))
    }

    if (settings.springBoot.enabled && settings.springBoot.applyBom) {
        val springBootVersion = settings.springBoot::version.schemaDelegate.asTraceableString()
        val springBootApplyBomTrace = TransformedValueTrace(
            description = "because Spring Boot is enabled and springBoot.applyBom=true",
            sourceValue = if (settings.springBoot::applyBom.isExplicitlySet) {
                settings.ktor::applyBom.schemaDelegate
            } else {
                settings.springBoot::enabled.schemaDelegate
            },
        )
        add(springBootBomDependency(springBootVersion, dependencyTrace = springBootApplyBomTrace))
    }

    if (module.type == ProductType.JVM_AMPER_PLUGIN) {
        // TODO: It'd be better to have some builtin dependency that resolves to a jar inside Amper distribution.
        add(MavenDependency(
            coordinates = TraceableString(
                value = "org.jetbrains.amper:amper-extensibility-api:${AmperBuild.mavenVersion}",
                trace = DefaultTrace,
            ),
            // TODO we should trace to the product type, but we don't seem to have this trace in the AOM
            trace = DefaultTrace,
        ))
    }
}

private fun Fragment.inferredTestDependencies(): List<MavenDependency> = buildList {
    val kotlinVersion = settings.kotlin::version.schemaDelegate.asTraceableString()
    if (platforms.size == 1 && platforms.single().supportsJvmTestFrameworks()) {
        val junitTrace = TransformedValueTrace(
            description = "because the test engine is ${settings.junit.schemaValue}",
            sourceValue = settings::junit.schemaDelegate,
        )
        when (settings.junit) {
            // TODO support kotlin-test-testng?
            //   For this, we should rename settings.junit -> settings.jvm.testFramework and add the TESTNG value to the enum
            JUnitVersion.JUNIT4 -> add(kotlinDependencyOf("kotlin-test-junit", kotlinVersion, junitTrace))
            JUnitVersion.JUNIT5 -> add(kotlinDependencyOf("kotlin-test-junit5", kotlinVersion, junitTrace))
            JUnitVersion.NONE -> add(kotlinDependencyOf("kotlin-test", kotlinVersion, DefaultTrace))
        }
    } else {
        add(kotlinDependencyOf("kotlin-test", kotlinVersion, DefaultTrace))
        add(kotlinDependencyOf("kotlin-test-annotations-common", kotlinVersion, DefaultTrace))
    }
}

private fun SchemaValueDelegate<String>.asTraceableString() = TraceableString(value, trace)

private fun Platform.supportsJvmTestFrameworks() = this == Platform.JVM || this == Platform.ANDROID

private val MavenDependencyBase.groupAndArtifact: String
    get() {
        val parts = coordinates.value.split(":", limit = 3)
        // Some tests don't have actual coordinates, maybe in real life we might also not have a group:artifact prefix.
        // This is not the place to fail if we want validation on maven coordinates, so we just go "best effort" here.
        return if (parts.size >= 2) "${parts[0]}:${parts[1]}" else coordinates.value
    }

private fun RepositoriesModulePart.withImplicitMavenRepositories(productType: ProductType, fragments: List<Fragment>): ModulePart<*> {
    val repositories = buildList {
        addAll(mavenRepositories)
        val isHotReloadRuntimeApiPresent = fragments
            .flatMap { it.externalDependencies }
            .filterIsInstance<MavenDependency>()
            .any { it.groupAndArtifact == "org.jetbrains.compose.hot-reload:hot-reload-runtime-api" }
        if (isHotReloadRuntimeApiPresent) {
            add(RepositoriesModulePart.Repository(
                id = "amper-hot-reload-dev",
                url = "https://packages.jetbrains.team/maven/p/amper/compose-hot-reload",
            ))
        }

        if (productType == ProductType.JVM_AMPER_PLUGIN) {
            if (AmperBuild.isSNAPSHOT) {
                add(RepositoriesModulePart.Repository(
                    id = "maven-local-resolve",
                    url = SpecialMavenLocalUrl,
                ))
            } else {
                add(RepositoriesModulePart.Repository(
                    id = "amper-maven",
                    url = "https://packages.jetbrains.team/maven/p/amper/amper",
                ))
            }
        }
    }
    return RepositoriesModulePart(
        mavenRepositories = repositories,
    )
}
