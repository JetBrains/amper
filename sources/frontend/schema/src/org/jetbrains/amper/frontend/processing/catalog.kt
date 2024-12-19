/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.api.withComputedValueTrace
import org.jetbrains.amper.frontend.api.withTraceFrom
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.CatalogKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.KspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.MavenKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Modifiers

/**
 * Replace all [CatalogDependency] with ones, that are from actual catalog.
 */
context(ProblemReporterContext)
fun <T: Base> T.replaceCatalogDependencies(
    catalog: VersionCatalog,
) = apply {
    // Actual replacement.
    dependencies = dependencies?.replaceCatalogDeps(catalog)
    `test-dependencies` = `test-dependencies`?.replaceCatalogDeps(catalog)

    settings.values.forEach { fragmentSettings ->
        val kspSettings = fragmentSettings.kotlin.ksp
        kspSettings.processors = kspSettings.processors.convertCatalogProcessors(catalog)
    }
}

context(ProblemReporterContext)
private fun Map<Modifiers, List<Dependency>>.replaceCatalogDeps(catalog: VersionCatalog) =
    entries.associate { it.key to it.value.convertCatalogDeps(catalog) }

context(ProblemReporterContext)
private fun List<Dependency>.convertCatalogDeps(catalog: VersionCatalog) = mapNotNull {
    if (it is CatalogDependency) it.convertCatalogDep(catalog) else it
}

context(ProblemReporterContext)
private fun CatalogDependency.convertCatalogDep(catalog: VersionCatalog): Dependency? {
    val catalogDep = this
    val catalogueKeyWithTrace = TraceableString(catalogDep.catalogKey).withTraceFrom(catalogDep::catalogKey.valueBase)
    val catalogValue = catalog.findInCatalogWithReport(catalogueKeyWithTrace) ?: return null
    return ExternalMavenDependency().apply {
        copyFrom(catalogDep)
        coordinates = catalogValue.value
        this::coordinates.valueBase?.withTraceFrom(catalogDep::catalogKey.valueBase)?.withComputedValueTrace(catalogValue)
    }.withTraceFrom(catalogDep)
}

internal fun Dependency.copyFrom(other: Dependency) {
    exported = other.exported
    this::exported.valueBase?.withTraceFrom(other::exported.valueBase)
    scope = other.scope
    this::scope.valueBase?.withTraceFrom(other::scope.valueBase)

    withTraceFrom(other)
}

context(ProblemReporterContext)
private fun List<KspProcessorDeclaration>.convertCatalogProcessors(
    catalog: VersionCatalog,
): List<KspProcessorDeclaration> = mapNotNull {
    if (it is CatalogKspProcessorDeclaration) it.convertCatalogProcessor(catalog) else it
}

context(ProblemReporterContext)
private fun CatalogKspProcessorDeclaration.convertCatalogProcessor(catalog: VersionCatalog): MavenKspProcessorDeclaration? {
    val catalogValue = catalog.findInCatalogWithReport(catalogKey) ?: return null
    return MavenKspProcessorDeclaration(catalogValue)
}

open class PredefinedCatalog(
    override val entries: Map<String, TraceableString>
) : VersionCatalog {
    override val isPhysical: Boolean = false

    constructor(builder: MutableMap<String, TraceableString>.() -> Unit)
            : this(buildMap(builder)) {

            }

    override fun findInCatalog(key: String): TraceableString? = entries[key]
}

/**
 * Composition of multiple version catalogs with priority for first declared.
 */
class CompositeVersionCatalog(
    private val catalogs: List<VersionCatalog>
) : VersionCatalog {

    override val entries: Map<String, TraceableString> = buildMap {
        // First catalogs have the highest priority.
        catalogs.reversed().forEach { putAll(it.entries) }
    }

    override val isPhysical: Boolean
        get() = catalogs.any { it.isPhysical }

    override fun findInCatalog(key: String) = catalogs.firstNotNullOfOrNull { it.findInCatalog(key) }
}

class BuiltInCatalog(
    serializationVersion: TraceableString?,
    composeVersion: TraceableString?,
    private val systemInfo: SystemInfo = DefaultSystemInfo,
) : PredefinedCatalog({
    // Add Kotlin dependencies that should be aligned with our single Kotlin version
    val kotlinVersion = UsedVersions.kotlinVersion
    put("kotlin-test-junit5", library("org.jetbrains.kotlin:kotlin-test-junit5", kotlinVersion))
    put("kotlin-test-junit", library("org.jetbrains.kotlin:kotlin-test-junit", kotlinVersion))
    put("kotlin-test", library("org.jetbrains.kotlin:kotlin-test", kotlinVersion))

    put("kotlin.test", library("org.jetbrains.kotlin:kotlin-test", kotlinVersion))
    put("kotlin.test.junit", library("org.jetbrains.kotlin:kotlin-test-junit", kotlinVersion))
    put("kotlin.test.junit5", library("org.jetbrains.kotlin:kotlin-test-junit5", kotlinVersion))
    put("kotlin.reflect", library("org.jetbrains.kotlin:kotlin-reflect", kotlinVersion))

    if (serializationVersion != null) {
        put("kotlin.serialization.core", library("org.jetbrains.kotlinx:kotlinx-serialization-core", serializationVersion))
        put("kotlin.serialization.cbor", library("org.jetbrains.kotlinx:kotlinx-serialization-cbor", serializationVersion))
        put("kotlin.serialization.hocon", library("org.jetbrains.kotlinx:kotlinx-serialization-hocon", serializationVersion))
        put("kotlin.serialization.json", library("org.jetbrains.kotlinx:kotlinx-serialization-json", serializationVersion))
        put("kotlin.serialization.json-okio", library("org.jetbrains.kotlinx:kotlinx-serialization-json-okio", serializationVersion))
        put("kotlin.serialization.properties", library("org.jetbrains.kotlinx:kotlinx-serialization-properties", serializationVersion))
        put("kotlin.serialization.protobuf", library("org.jetbrains.kotlinx:kotlinx-serialization-protobuf", serializationVersion))
    }

    // Add compose.
    if (composeVersion != null) {
        put("compose.animation", library("org.jetbrains.compose.animation:animation", composeVersion))
        put("compose.animationGraphics", library("org.jetbrains.compose.animation:animation-graphics", composeVersion))
        put("compose.foundation", library("org.jetbrains.compose.foundation:foundation", composeVersion))
        put("compose.material", library("org.jetbrains.compose.material:material", composeVersion))
        put("compose.material3", library("org.jetbrains.compose.material3:material3", composeVersion))
        put("compose.runtime", library("org.jetbrains.compose.runtime:runtime", composeVersion))
        put("compose.runtimeSaveable", library("org.jetbrains.compose.runtime:runtime-saveable", composeVersion))
        put("compose.ui", library("org.jetbrains.compose.ui:ui", composeVersion))
        put("compose.uiTest", library("org.jetbrains.compose.ui:ui-test", composeVersion))
        put("compose.uiTooling", library("org.jetbrains.compose.ui:ui-tooling", composeVersion))
        put("compose.preview", library("org.jetbrains.compose.ui:ui-tooling-preview", composeVersion))
        put("compose.materialIconsExtended", library("org.jetbrains.compose.material:material-icons-extended", composeVersion))
        put("compose.components.resources", library("org.jetbrains.compose.components:components-resources", composeVersion))
        put("compose.html.svg", library("org.jetbrains.compose.html:html-svg", composeVersion))
        put("compose.html.testUtils", library("org.jetbrains.compose.html:html-test-utils", composeVersion))
        put("compose.html.core", library("org.jetbrains.compose.html:html-core", composeVersion))
        put("compose.desktop.components.splitPane", library("org.jetbrains.compose.components:components-splitpane", composeVersion))
        put("compose.desktop.components.animatedImage", library("org.jetbrains.compose.components:components-animatedimage", composeVersion))
        put("compose.desktop.common", library("org.jetbrains.compose.desktop:desktop", composeVersion))
        put("compose.desktop.linux_x64", library("org.jetbrains.compose.desktop:desktop-jvm-linux-x64", composeVersion))
        put("compose.desktop.linux_arm64", library("org.jetbrains.compose.desktop:desktop-jvm-linux-arm64", composeVersion))
        put("compose.desktop.windows_x64", library("org.jetbrains.compose.desktop:desktop-jvm-windows-x64", composeVersion))
        put("compose.desktop.macos_x64", library("org.jetbrains.compose.desktop:desktop-jvm-macos-x64", composeVersion))
        put("compose.desktop.macos_arm64", library("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64", composeVersion))
        put("compose.desktop.uiTestJUnit4", library("org.jetbrains.compose.ui:ui-test-junit4", composeVersion))
        put("compose.desktop.currentOs", library("org.jetbrains.compose.desktop:desktop-jvm-${systemInfo.detect().familyArch}", composeVersion))
    }

}) {
    init {
        entries.forEach {
            it.value.trace = (it.value.trace as? BuiltinCatalogTrace)?.copy(catalog = this)
        }
    }
}

fun library(groupAndModule: String, version: TraceableString): TraceableString =
    TraceableString("$groupAndModule:${version.value}").apply { trace = BuiltinCatalogTrace(EmptyCatalog, computedValueTrace = version) }

fun library(groupAndModule: String, version: String): TraceableString =
    TraceableString("$groupAndModule:$version").apply { trace = BuiltinCatalogTrace(EmptyCatalog, null) }

private object EmptyCatalog: VersionCatalog {
    override val entries: Map<String, TraceableString> = emptyMap()
    override val isPhysical: Boolean = false
    override fun findInCatalog(key: String): TraceableString? = null
}

