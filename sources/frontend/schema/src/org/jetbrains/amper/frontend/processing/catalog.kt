/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.UsedVersions.logbackVersion
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
    ktorVersion: TraceableString?,
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

    // add ktor
    if (ktorVersion != null) {
        // server
        put("ktor.server", library("io.ktor:ktor-server", ktorVersion))
        put("ktor.server.auth", library("io.ktor:ktor-server-auth", ktorVersion))
        put("ktor.server.auth.jwt", library("io.ktor:ktor-server-auth-jwt", ktorVersion))
        put("ktor.server.auth.ldap", library("io.ktor:ktor-server-auth-ldap", ktorVersion))
        put("ktor.server.autoHeadResponse", library("io.ktor:ktor-server-auto-head-response", ktorVersion))
        put("ktor.server.bodyLimit", library("io.ktor:ktor-server-body-limit", ktorVersion))
        put("ktor.server.cachingHeaders", library("io.ktor:ktor-server-caching-headers", ktorVersion))
        put("ktor.server.callId", library("io.ktor:ktor-server-call-id", ktorVersion))
        put("ktor.server.callLogging", library("io.ktor:ktor-server-call-logging", ktorVersion))
        put("ktor.server.cio", library("io.ktor:ktor-server-cio", ktorVersion))
        put("ktor.server.compression", library("io.ktor:ktor-server-compression", ktorVersion))
        put("ktor.server.conditionalHeaders", library("io.ktor:ktor-server-conditional-headers", ktorVersion))
        put("ktor.server.configYaml", library("io.ktor:ktor-server-config-yaml", ktorVersion))
        put("ktor.server.contentNegotiation", library("io.ktor:ktor-server-content-negotiation", ktorVersion))
        put("ktor.server.core", library("io.ktor:ktor-server-core", ktorVersion))
        put("ktor.server.cors", library("io.ktor:ktor-server-cors", ktorVersion))
        put("ktor.server.csrf", library("io.ktor:ktor-server-csrf", ktorVersion))
        put("ktor.server.dataConversion", library("io.ktor:ktor-server-data-conversion", ktorVersion))
        put("ktor.server.defaultHeaders", library("io.ktor:ktor-server-default-headers", ktorVersion))
        put("ktor.server.doubleReceive", library("io.ktor:ktor-server-double-receive", ktorVersion))
        put("ktor.server.forwardedHeader", library("io.ktor:ktor-server-forwarded-header", ktorVersion))
        put("ktor.server.freemarker", library("io.ktor:ktor-server-freemarker", ktorVersion))
        put("ktor.server.hsts", library("io.ktor:ktor-server-hsts", ktorVersion))
        put("ktor.server.htmlBuilder", library("io.ktor:ktor-server-html-builder", ktorVersion))
        put("ktor.server.httpRedirect", library("io.ktor:ktor-server-http-redirect", ktorVersion))
        put("ktor.server.i18n", library("io.ktor:ktor-server-i18n", ktorVersion))
        put("ktor.server.jetty", library("io.ktor:ktor-server-jetty", ktorVersion))
        put("ktor.server.jetty.jakarta", library("io.ktor:ktor-server-jetty-jakarta", ktorVersion))
        put("ktor.server.jte", library("io.ktor:ktor-server-jte", ktorVersion))
        put("ktor.server.locations", library("io.ktor:ktor-server-locations", ktorVersion))
        put("ktor.server.methodOverride", library("io.ktor:ktor-server-method-override", ktorVersion))
        put("ktor.server.metrics", library("io.ktor:ktor-server-metrics", ktorVersion))
        put("ktor.server.metrics.micrometer", library("io.ktor:ktor-server-metrics-micrometer", ktorVersion))
        put("ktor.server.mustache", library("io.ktor:ktor-server-mustache", ktorVersion))
        put("ktor.server.netty", library("io.ktor:ktor-server-netty", ktorVersion))
        put("ktor.server.openapi", library("io.ktor:ktor-server-openapi", ktorVersion))
        put("ktor.server.partialContent", library("io.ktor:ktor-server-partial-content", ktorVersion))
        put("ktor.server.pebble", library("io.ktor:ktor-server-pebble", ktorVersion))
        put("ktor.server.plugins", library("io.ktor:ktor-server-plugins", ktorVersion))
        put("ktor.server.rateLimit", library("io.ktor:ktor-server-rate-limit", ktorVersion))
        put("ktor.server.requestValidation", library("io.ktor:ktor-server-request-validation", ktorVersion))
        put("ktor.server.resources", library("io.ktor:ktor-server-resources", ktorVersion))
        put("ktor.server.servlet", library("io.ktor:ktor-server-servlet", ktorVersion))
        put("ktor.server.servlet.jakarta", library("io.ktor:ktor-server-servlet-jakarta", ktorVersion))
        put("ktor.server.sessions", library("io.ktor:ktor-server-sessions", ktorVersion))
        put("ktor.server.sse", library("io.ktor:ktor-server-sse", ktorVersion))
        put("ktor.server.statusPages", library("io.ktor:ktor-server-status-pages", ktorVersion))
        put("ktor.server.swagger", library("io.ktor:ktor-server-swagger", ktorVersion))
        put("ktor.server.thymeleaf", library("io.ktor:ktor-server-thymeleaf", ktorVersion))
        put("ktor.server.tomcat", library("io.ktor:ktor-server-tomcat", ktorVersion))
        put("ktor.server.tomcat.jakarta", library("io.ktor:ktor-server-tomcat-jakarta", ktorVersion))
        put("ktor.server.velocity", library("io.ktor:ktor-server-velocity", ktorVersion))
        put("ktor.server.webjars", library("io.ktor:ktor-server-webjars", ktorVersion))
        put("ktor.server.websockets", library("io.ktor:ktor-server-websockets", ktorVersion))
        put("ktor.auth", library("io.ktor:ktor-auth", ktorVersion))
        put("ktor.auth.jwt", library("io.ktor:ktor-auth-jwt", ktorVersion))
        put("ktor.auth.ldap", library("io.ktor:ktor-auth-ldap", ktorVersion))
        put("ktor.freemarker", library("io.ktor:ktor-freemarker", ktorVersion))
        put("ktor.html.builder", library("io.ktor:ktor-html-builder", ktorVersion))
        put("ktor.http", library("io.ktor:ktor-http", ktorVersion))
        put("ktor.http.cio", library("io.ktor:ktor-http-cio", ktorVersion))
        put("ktor.locations", library("io.ktor:ktor-locations", ktorVersion))
        put("ktor.mustache", library("io.ktor:ktor-mustache", ktorVersion))
        put("ktor.pebble", library("io.ktor:ktor-pebble", ktorVersion))
        put("ktor.thymeleaf", library("io.ktor:ktor-thymeleaf", ktorVersion))
        put("ktor.velocity", library("io.ktor:ktor-velocity", ktorVersion))
        put("ktor.webjars", library("io.ktor:ktor-webjars", ktorVersion))
        put("ktor.websockets", library("io.ktor:ktor-websockets", ktorVersion))

        // client
        put("ktor.client", library("io.ktor:ktor-client", ktorVersion))
        put("ktor.client.apache", library("io.ktor:ktor-client-apache", ktorVersion))
        put("ktor.client.apache5", library("io.ktor:ktor-client-apache5", ktorVersion))
        put("ktor.client.auth", library("io.ktor:ktor-client-auth", ktorVersion))
        put("ktor.client.authBasic", library("io.ktor:ktor-client-auth-basic", ktorVersion))
        put("ktor.client.callId", library("io.ktor:ktor-client-call-id", ktorVersion))
        put("ktor.client.cio", library("io.ktor:ktor-client-cio", ktorVersion))
        put("ktor.client.contentNegotiation", library("io.ktor:ktor-client-content-negotiation", ktorVersion))
        put("ktor.client.core", library("io.ktor:ktor-client-core", ktorVersion))
        put("ktor.client.encoding", library("io.ktor:ktor-client-encoding", ktorVersion))
        put("ktor.client.features", library("io.ktor:ktor-client-features", ktorVersion))
        put("ktor.client.gson", library("io.ktor:ktor-client-gson", ktorVersion))
        put("ktor.client.jackson", library("io.ktor:ktor-client-jackson", ktorVersion))
        put("ktor.client.java", library("io.ktor:ktor-client-java", ktorVersion))
        put("ktor.client.jetty", library("io.ktor:ktor-client-jetty", ktorVersion))
        put("ktor.client.jetty.jakarta", library("io.ktor:ktor-client-jetty-jakarta", ktorVersion))
        put("ktor.client.json", library("io.ktor:ktor-client-json", ktorVersion))
        put("ktor.client.logging", library("io.ktor:ktor-client-logging", ktorVersion))
        put("ktor.client.mock", library("io.ktor:ktor-client-mock", ktorVersion))
        put("ktor.client.okhttp", library("io.ktor:ktor-client-okhttp", ktorVersion))
        put("ktor.client.plugins", library("io.ktor:ktor-client-plugins", ktorVersion))
        put("ktor.client.resources", library("io.ktor:ktor-client-resources", ktorVersion))
        put("ktor.client.serialization", library("io.ktor:ktor-client-serialization", ktorVersion))
        put("ktor.client.websocket", library("io.ktor:ktor-client-websocket", ktorVersion))
        put("ktor.client.websockets", library("io.ktor:ktor-client-websockets", ktorVersion))
        put("ktor.client.winhttp", library("io.ktor:ktor-client-winhttp", ktorVersion))

        // other
        put("ktor.bom", library("bom:io.ktor:ktor-bom", ktorVersion))
        put("ktor.callId", library("io.ktor:ktor-call-id", ktorVersion))
        put("ktor.events", library("io.ktor:ktor-events", ktorVersion))
        put("ktor.features", library("io.ktor:ktor-features", ktorVersion))
        put("ktor.gson", library("io.ktor:ktor-gson", ktorVersion))
        put("ktor.io", library("io.ktor:ktor-io", ktorVersion))
        put("ktor.jackson", library("io.ktor:ktor-jackson", ktorVersion))
        put("ktor.metrics", library("io.ktor:ktor-metrics", ktorVersion))
        put("ktor.metrics.micrometer", library("io.ktor:ktor-metrics-micrometer", ktorVersion))
        put("ktor.network", library("io.ktor:ktor-network", ktorVersion))
        put("ktor.network.tls", library("io.ktor:ktor-network-tls", ktorVersion))
        put("ktor.network.tls.certificates", library("io.ktor:ktor-network-tls-certificates", ktorVersion))
        put("ktor.resources", library("io.ktor:ktor-resources", ktorVersion))
        put("ktor.serialization", library("io.ktor:ktor-serialization", ktorVersion))
        put("ktor.serialization.gson", library("io.ktor:ktor-serialization-gson", ktorVersion))
        put("ktor.serialization.jackson", library("io.ktor:ktor-serialization-jackson", ktorVersion))
        put("ktor.serialization.kotlinx", library("io.ktor:ktor-serialization-kotlinx", ktorVersion))
        put("ktor.serialization.kotlinx.cbor", library("io.ktor:ktor-serialization-kotlinx-cbor", ktorVersion))
        put("ktor.serialization.kotlinx.json", library("io.ktor:ktor-serialization-kotlinx-json", ktorVersion))
        put("ktor.serialization.kotlinx.protobuf", library("io.ktor:ktor-serialization-kotlinx-protobuf", ktorVersion))
        put("ktor.serialization.kotlinx.xml", library("io.ktor:ktor-serialization-kotlinx-xml", ktorVersion))
        put("ktor.sse", library("io.ktor:ktor-sse", ktorVersion))
        put("ktor.utils", library("io.ktor:ktor-utils", ktorVersion))
        put("ktor.websocket.serialization", library("io.ktor:ktor-websocket-serialization", ktorVersion))
    }

    put("logback.classic", library("ch.qos.logback:logback-classic", logbackVersion))

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

