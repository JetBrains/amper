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
import org.jetbrains.amper.frontend.api.toTraceableString
import org.jetbrains.amper.frontend.api.withTraceFrom
import org.jetbrains.amper.frontend.schema.Base
import org.jetbrains.amper.frontend.schema.CatalogDependency
import org.jetbrains.amper.frontend.schema.CatalogKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Dependency
import org.jetbrains.amper.frontend.schema.ExternalMavenDependency
import org.jetbrains.amper.frontend.schema.MavenKspProcessorDeclaration
import org.jetbrains.amper.frontend.schema.Modifiers
import org.jetbrains.amper.frontend.schema.KspProcessorDeclaration

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
    val catalogValue = catalog.findInCatalogWithReport(catalogDep::catalogKey.toTraceableString()) ?: return null
    return ExternalMavenDependency().apply {
        coordinates = catalogValue.value
        exported = catalogDep.exported
        scope = catalogDep.scope
    }.withTraceFrom(catalogValue)
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

    constructor(builder: MutableMap<String, String>.() -> Unit) :
            this(buildMap(builder).map { it.key to TraceableString(it.value) }.toMap())

    override fun findInCatalog(key: TraceableString): TraceableString? = entries[key.value]
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

    override fun findInCatalog(
        key: TraceableString,
    ) = catalogs.firstNotNullOfOrNull { it.findInCatalog(key) }
}

class BuiltInCatalog(
    serializationVersion: String?,
    composeVersion: String?,
    private val systemInfo: SystemInfo = DefaultSystemInfo,
) : PredefinedCatalog({
    // Add Kotlin dependencies that should be aligned with our single Kotlin version
    val kotlinVersion = UsedVersions.kotlinVersion
    put("kotlin-test-junit5", "org.jetbrains.kotlin:kotlin-test-junit5:$kotlinVersion")
    put("kotlin-test-junit", "org.jetbrains.kotlin:kotlin-test-junit:$kotlinVersion")
    put("kotlin-test", "org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    put("kotlin-reflect", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")

    if (serializationVersion != null) {
        put("kotlinx.serialization.core", "org.jetbrains.kotlinx:kotlinx-serialization-core:$serializationVersion")
        put("kotlinx.serialization.cbor", "org.jetbrains.kotlinx:kotlinx-serialization-cbor:$serializationVersion")
        put("kotlinx.serialization.hocon", "org.jetbrains.kotlinx:kotlinx-serialization-hocon:$serializationVersion")
        put("kotlinx.serialization.json", "org.jetbrains.kotlinx:kotlinx-serialization-json:$serializationVersion")
        put("kotlinx.serialization.json-okio", "org.jetbrains.kotlinx:kotlinx-serialization-json-okio:$serializationVersion")
        put("kotlinx.serialization.properties", "org.jetbrains.kotlinx:kotlinx-serialization-properties:$serializationVersion")
        put("kotlinx.serialization.protobuf", "org.jetbrains.kotlinx:kotlinx-serialization-protobuf:$serializationVersion")
    }

    // Add compose.
    if (composeVersion != null) {
        put("compose.animation", "org.jetbrains.compose.animation:animation:$composeVersion")
        put("compose.animationGraphics", "org.jetbrains.compose.animation:animation-graphics:$composeVersion")
        put("compose.foundation", "org.jetbrains.compose.foundation:foundation:$composeVersion")
        put("compose.material", "org.jetbrains.compose.material:material:$composeVersion")
        put("compose.material3", "org.jetbrains.compose.material3:material3:$composeVersion")
        put("compose.runtime", "org.jetbrains.compose.runtime:runtime:$composeVersion")
        put("compose.runtimeSaveable", "org.jetbrains.compose.runtime:runtime-saveable:$composeVersion")
        put("compose.ui", "org.jetbrains.compose.ui:ui:$composeVersion")
        put("compose.uiTooling", "org.jetbrains.compose.ui:ui-tooling:$composeVersion")
        put("compose.preview", "org.jetbrains.compose.ui:ui-tooling-preview:$composeVersion")
        put("compose.materialIconsExtended", "org.jetbrains.compose.material:material-icons-extended:$composeVersion")
        put("compose.components.resources", "org.jetbrains.compose.components:components-resources:$composeVersion")
        put("compose.html.svg", "org.jetbrains.compose.html:html-svg:$composeVersion")
        put("compose.html.testUtils", "org.jetbrains.compose.html:html-test-utils:$composeVersion")
        put("compose.html.core", "org.jetbrains.compose.html:html-core:$composeVersion")
        put("compose.desktop.components.splitPane", "org.jetbrains.compose.components:components-splitpane:$composeVersion")
        put("compose.desktop.components.animatedImage", "org.jetbrains.compose.components:components-animatedimage:$composeVersion")
        put("compose.desktop.common", "org.jetbrains.compose.desktop:desktop:$composeVersion")
        put("compose.desktop.linux_x64", "org.jetbrains.compose.desktop:desktop-jvm-linux-x64:$composeVersion")
        put("compose.desktop.linux_arm64", "org.jetbrains.compose.desktop:desktop-jvm-linux-arm64:$composeVersion")
        put("compose.desktop.windows_x64", "org.jetbrains.compose.desktop:desktop-jvm-windows-x64:$composeVersion")
        put("compose.desktop.macos_x64", "org.jetbrains.compose.desktop:desktop-jvm-macos-x64:$composeVersion")
        put("compose.desktop.macos_arm64", "org.jetbrains.compose.desktop:desktop-jvm-macos-arm64:$composeVersion")
        put("compose.desktop.uiTestJUnit4", "org.jetbrains.compose.ui:ui-test-junit4:$composeVersion")
        put("compose.desktop.currentOs", "org.jetbrains.compose.desktop:desktop-jvm-${systemInfo.detect().familyArch}:$composeVersion")
    }
}) {
    init {
        entries.forEach { it.value.trace = BuiltinCatalogTrace(this) }
    }
}
