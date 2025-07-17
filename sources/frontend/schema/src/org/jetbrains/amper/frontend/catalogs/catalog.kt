/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import com.intellij.openapi.vfs.VirtualFile
import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.UsedVersions.logbackVersion
import org.jetbrains.amper.core.messages.NonIdealDiagnostic
import org.jetbrains.amper.core.messages.ProblemReporter
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.aomBuilder.BuildCtx
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableVersion
import org.jetbrains.amper.frontend.api.valueBase
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Settings

/**
 * Try to find gradle catalog and compose it with built-in catalog.
 */
internal fun BuildCtx.tryGetCatalogFor(
    file: VirtualFile, settings: Settings?,
): VersionCatalog {
    val gradleCatalog = catalogFinder?.getCatalogPathFor(file)?.let { pathResolver.parseGradleVersionCatalog(it) }
    return with(problemReporter) {
        addBuiltInCatalog(settings, gradleCatalog)
    }
}

/**
 * Try to get used version catalog.
 */
context(problemReporter: ProblemReporter)
private fun addBuiltInCatalog(
    settings: Settings?, otherCatalog: VersionCatalog? = null,
): VersionCatalog {
    val compose = settings?.compose
    val serialization = settings?.kotlin?.serialization
    val ktor = settings?.ktor
    val springBoot = settings?.springBoot
    val builtInCatalog = BuiltInCatalog(
        serializationVersion = serialization?.takeIf { it.enabled }?.version
            ?.let { TraceableVersion(it, serialization::version.valueBase!!) }
            ?.let { version(it, UsedVersions.kotlinxSerializationVersion) },
        composeVersion = compose?.takeIf { it.enabled }?.version
            ?.let { TraceableVersion(it, compose::version.valueBase!!) }
            ?.let { version(it, UsedVersions.composeVersion) },
        ktorVersion = ktor?.takeIf { it.enabled }?.version
            ?.let { TraceableVersion(it, ktor::version.valueBase!!) }
            ?.let { version(it, UsedVersions.ktorVersion) },
        springBootVersion = springBoot?.takeIf { it.enabled }?.version
            ?.let { TraceableVersion(it, springBoot::version.valueBase!!) }
            ?.let { version(it, UsedVersions.springBootVersion) },
    )
    val catalogs = otherCatalog?.let { listOf(it) }.orEmpty() + builtInCatalog
    val compositeCatalog = CompositeVersionCatalog(catalogs)
    return compositeCatalog
}

context(problemReporter: ProblemReporter)
@OptIn(NonIdealDiagnostic::class)
private fun version(version: TraceableVersion, fallbackVersion: String): TraceableString {
    // we validate the version only for emptiness because maven artifacts allow any string as a version
    //  that's why we cannot provide a precise validation for non-empty strings
    return if (!version.value.isEmpty()) version
    else {
        problemReporter.reportBundleError(
            source = version.asBuildProblemSource(),
            messageKey = "empty.version.string",
        )
        // fallback to avoid double errors
        TraceableString(fallbackVersion)
    }
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
    springBootVersion: TraceableString?,
    private val systemInfo: SystemInfo = DefaultSystemInfo,
) : PredefinedCatalog({
        
    // @formatter:off
    // Add Kotlin dependencies that should be aligned with our single Kotlin version
    val kotlinVersion = UsedVersions.kotlinVersion
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
        put("compose.components.resources", library("org.jetbrains.compose.components:components-resources", composeVersion))
        put("compose.desktop.common", library("org.jetbrains.compose.desktop:desktop", composeVersion))
        put("compose.desktop.components.animatedImage", library("org.jetbrains.compose.components:components-animatedimage", composeVersion))
        put("compose.desktop.components.splitPane", library("org.jetbrains.compose.components:components-splitpane", composeVersion))
        put("compose.desktop.currentOs", library("org.jetbrains.compose.desktop:desktop-jvm-${systemInfo.detect().familyArch}", composeVersion))
        put("compose.desktop.linux_arm64", library("org.jetbrains.compose.desktop:desktop-jvm-linux-arm64", composeVersion))
        put("compose.desktop.linux_x64", library("org.jetbrains.compose.desktop:desktop-jvm-linux-x64", composeVersion))
        put("compose.desktop.macos_arm64", library("org.jetbrains.compose.desktop:desktop-jvm-macos-arm64", composeVersion))
        put("compose.desktop.macos_x64", library("org.jetbrains.compose.desktop:desktop-jvm-macos-x64", composeVersion))
        put("compose.desktop.uiTestJUnit4", library("org.jetbrains.compose.ui:ui-test-junit4", composeVersion))
        put("compose.desktop.windows_x64", library("org.jetbrains.compose.desktop:desktop-jvm-windows-x64", composeVersion))
        put("compose.foundation", library("org.jetbrains.compose.foundation:foundation", composeVersion))
        put("compose.html.core", library("org.jetbrains.compose.html:html-core", composeVersion))
        put("compose.html.svg", library("org.jetbrains.compose.html:html-svg", composeVersion))
        put("compose.html.testUtils", library("org.jetbrains.compose.html:html-test-utils", composeVersion))
        put("compose.material", library("org.jetbrains.compose.material:material", composeVersion))
        put("compose.material3", library("org.jetbrains.compose.material3:material3", composeVersion))
        put("compose.materialIconsCore", library("org.jetbrains.compose.material:material-icons-core", materialIconsVersion(composeVersion)))
        put("compose.materialIconsExtended", library("org.jetbrains.compose.material:material-icons-extended", materialIconsVersion(composeVersion)))
        put("compose.preview", library("org.jetbrains.compose.ui:ui-tooling-preview", composeVersion))
        put("compose.preview", library("org.jetbrains.compose.ui:ui-tooling-preview", composeVersion))
        put("compose.runtime", library("org.jetbrains.compose.runtime:runtime", composeVersion))
        put("compose.runtimeSaveable", library("org.jetbrains.compose.runtime:runtime-saveable", composeVersion))
        put("compose.ui", library("org.jetbrains.compose.ui:ui", composeVersion))
        put("compose.uiTest", library("org.jetbrains.compose.ui:ui-test", composeVersion))
        put("compose.uiTooling", library("org.jetbrains.compose.ui:ui-tooling", composeVersion))
        
    }

    // add ktor
    if (ktorVersion != null) {
        // bom
        put("ktor.bom", library("io.ktor:ktor-bom", ktorVersion))

        // server
        put("ktor.auth", library("io.ktor:ktor-auth"))
        put("ktor.auth.jwt", library("io.ktor:ktor-auth-jwt"))
        put("ktor.auth.ldap", library("io.ktor:ktor-auth-ldap"))
        put("ktor.freemarker", library("io.ktor:ktor-freemarker"))
        put("ktor.html.builder", library("io.ktor:ktor-html-builder"))
        put("ktor.http", library("io.ktor:ktor-http"))
        put("ktor.http.cio", library("io.ktor:ktor-http-cio"))
        put("ktor.locations", library("io.ktor:ktor-locations"))
        put("ktor.mustache", library("io.ktor:ktor-mustache"))
        put("ktor.pebble", library("io.ktor:ktor-pebble"))
        put("ktor.server.auth", library("io.ktor:ktor-server-auth"))
        put("ktor.server.auth.jwt", library("io.ktor:ktor-server-auth-jwt"))
        put("ktor.server.auth.ldap", library("io.ktor:ktor-server-auth-ldap"))
        put("ktor.server.autoHeadResponse", library("io.ktor:ktor-server-auto-head-response"))
        put("ktor.server.bodyLimit", library("io.ktor:ktor-server-body-limit"))
        put("ktor.server.cachingHeaders", library("io.ktor:ktor-server-caching-headers"))
        put("ktor.server.callId", library("io.ktor:ktor-server-call-id"))
        put("ktor.server.callLogging", library("io.ktor:ktor-server-call-logging"))
        put("ktor.server.cio", library("io.ktor:ktor-server-cio"))
        put("ktor.server.compression", library("io.ktor:ktor-server-compression"))
        put("ktor.server.conditionalHeaders", library("io.ktor:ktor-server-conditional-headers"))
        put("ktor.server.configYaml", library("io.ktor:ktor-server-config-yaml"))
        put("ktor.server.contentNegotiation", library("io.ktor:ktor-server-content-negotiation"))
        put("ktor.server.core", library("io.ktor:ktor-server-core"))
        put("ktor.server.cors", library("io.ktor:ktor-server-cors"))
        put("ktor.server.csrf", library("io.ktor:ktor-server-csrf"))
        put("ktor.server.dataConversion", library("io.ktor:ktor-server-data-conversion"))
        put("ktor.server.defaultHeaders", library("io.ktor:ktor-server-default-headers"))
        put("ktor.server.doubleReceive", library("io.ktor:ktor-server-double-receive"))
        put("ktor.server.forwardedHeader", library("io.ktor:ktor-server-forwarded-header"))
        put("ktor.server.freemarker", library("io.ktor:ktor-server-freemarker"))
        put("ktor.server.hsts", library("io.ktor:ktor-server-hsts"))
        put("ktor.server.htmlBuilder", library("io.ktor:ktor-server-html-builder"))
        put("ktor.server.httpRedirect", library("io.ktor:ktor-server-http-redirect"))
        put("ktor.server.i18n", library("io.ktor:ktor-server-i18n"))
        put("ktor.server.jetty", library("io.ktor:ktor-server-jetty"))
        put("ktor.server.jetty.jakarta", library("io.ktor:ktor-server-jetty-jakarta"))
        put("ktor.server.jte", library("io.ktor:ktor-server-jte"))
        put("ktor.server.locations", library("io.ktor:ktor-server-locations"))
        put("ktor.server.methodOverride", library("io.ktor:ktor-server-method-override"))
        put("ktor.server.metrics", library("io.ktor:ktor-server-metrics"))
        put("ktor.server.metrics.micrometer", library("io.ktor:ktor-server-metrics-micrometer"))
        put("ktor.server.mustache", library("io.ktor:ktor-server-mustache"))
        put("ktor.server.netty", library("io.ktor:ktor-server-netty"))
        put("ktor.server.openapi", library("io.ktor:ktor-server-openapi"))
        put("ktor.server.partialContent", library("io.ktor:ktor-server-partial-content"))
        put("ktor.server.pebble", library("io.ktor:ktor-server-pebble"))
        put("ktor.server.plugins", library("io.ktor:ktor-server-plugins"))
        put("ktor.server.rateLimit", library("io.ktor:ktor-server-rate-limit"))
        put("ktor.server.requestValidation", library("io.ktor:ktor-server-request-validation"))
        put("ktor.server.resources", library("io.ktor:ktor-server-resources"))
        put("ktor.server.servlet", library("io.ktor:ktor-server-servlet"))
        put("ktor.server.servlet.jakarta", library("io.ktor:ktor-server-servlet-jakarta"))
        put("ktor.server.sessions", library("io.ktor:ktor-server-sessions"))
        put("ktor.server.sse", library("io.ktor:ktor-server-sse"))
        put("ktor.server.statusPages", library("io.ktor:ktor-server-status-pages"))
        put("ktor.server.swagger", library("io.ktor:ktor-server-swagger"))
        put("ktor.server.testHost", library("io.ktor:ktor-server-test-host"))
        put("ktor.server.thymeleaf", library("io.ktor:ktor-server-thymeleaf"))
        put("ktor.server.tomcat", library("io.ktor:ktor-server-tomcat"))
        put("ktor.server.tomcat.jakarta", library("io.ktor:ktor-server-tomcat-jakarta"))
        put("ktor.server.velocity", library("io.ktor:ktor-server-velocity"))
        put("ktor.server.webjars", library("io.ktor:ktor-server-webjars"))
        put("ktor.server.websockets", library("io.ktor:ktor-server-websockets"))
        put("ktor.thymeleaf", library("io.ktor:ktor-thymeleaf"))
        put("ktor.velocity", library("io.ktor:ktor-velocity"))
        put("ktor.webjars", library("io.ktor:ktor-webjars"))
        put("ktor.websockets", library("io.ktor:ktor-websockets"))

        // client
        put("ktor.client", library("io.ktor:ktor-client"))
        put("ktor.client.apache", library("io.ktor:ktor-client-apache"))
        put("ktor.client.apache5", library("io.ktor:ktor-client-apache5"))
        put("ktor.client.auth", library("io.ktor:ktor-client-auth"))
        put("ktor.client.authBasic", library("io.ktor:ktor-client-auth-basic"))
        put("ktor.client.callId", library("io.ktor:ktor-client-call-id"))
        put("ktor.client.cio", library("io.ktor:ktor-client-cio"))
        put("ktor.client.contentNegotiation", library("io.ktor:ktor-client-content-negotiation"))
        put("ktor.client.core", library("io.ktor:ktor-client-core"))
        put("ktor.client.encoding", library("io.ktor:ktor-client-encoding"))
        put("ktor.client.features", library("io.ktor:ktor-client-features"))
        put("ktor.client.gson", library("io.ktor:ktor-client-gson"))
        put("ktor.client.jackson", library("io.ktor:ktor-client-jackson"))
        put("ktor.client.java", library("io.ktor:ktor-client-java"))
        put("ktor.client.jetty", library("io.ktor:ktor-client-jetty"))
        put("ktor.client.jetty.jakarta", library("io.ktor:ktor-client-jetty-jakarta"))
        put("ktor.client.json", library("io.ktor:ktor-client-json"))
        put("ktor.client.logging", library("io.ktor:ktor-client-logging"))
        put("ktor.client.mock", library("io.ktor:ktor-client-mock"))
        put("ktor.client.okhttp", library("io.ktor:ktor-client-okhttp"))
        put("ktor.client.plugins", library("io.ktor:ktor-client-plugins"))
        put("ktor.client.resources", library("io.ktor:ktor-client-resources"))
        put("ktor.client.serialization", library("io.ktor:ktor-client-serialization"))
        put("ktor.client.websocket", library("io.ktor:ktor-client-websocket"))
        put("ktor.client.websockets", library("io.ktor:ktor-client-websockets"))
        put("ktor.client.winhttp", library("io.ktor:ktor-client-winhttp"))

        // other
        put("ktor.callId", library("io.ktor:ktor-call-id"))
        put("ktor.events", library("io.ktor:ktor-events"))
        put("ktor.features", library("io.ktor:ktor-features"))
        put("ktor.gson", library("io.ktor:ktor-gson"))
        put("ktor.io", library("io.ktor:ktor-io"))
        put("ktor.jackson", library("io.ktor:ktor-jackson"))
        put("ktor.metrics", library("io.ktor:ktor-metrics"))
        put("ktor.metrics.micrometer", library("io.ktor:ktor-metrics-micrometer"))
        put("ktor.network", library("io.ktor:ktor-network"))
        put("ktor.network.tls", library("io.ktor:ktor-network-tls"))
        put("ktor.network.tls.certificates", library("io.ktor:ktor-network-tls-certificates"))
        put("ktor.resources", library("io.ktor:ktor-resources"))
        put("ktor.serialization", library("io.ktor:ktor-serialization"))
        put("ktor.serialization.gson", library("io.ktor:ktor-serialization-gson"))
        put("ktor.serialization.jackson", library("io.ktor:ktor-serialization-jackson"))
        put("ktor.serialization.kotlinx", library("io.ktor:ktor-serialization-kotlinx"))
        put("ktor.serialization.kotlinx.cbor", library("io.ktor:ktor-serialization-kotlinx-cbor"))
        put("ktor.serialization.kotlinx.json", library("io.ktor:ktor-serialization-kotlinx-json"))
        put("ktor.serialization.kotlinx.protobuf", library("io.ktor:ktor-serialization-kotlinx-protobuf"))
        put("ktor.serialization.kotlinx.xml", library("io.ktor:ktor-serialization-kotlinx-xml"))
        put("ktor.sse", library("io.ktor:ktor-sse"))
        put("ktor.utils", library("io.ktor:ktor-utils"))
        put("ktor.websocket.serialization", library("io.ktor:ktor-websocket-serialization"))
        
        put("logback.classic", library("ch.qos.logback:logback-classic", logbackVersion))
    }

    // Spring Boot dependencies
    if (springBootVersion != null) {
        // boms
        put("spring.boot.bom", library("org.springframework.boot:spring-boot-dependencies", springBootVersion))
        put("spring.shell.bom", library("org.springframework.shell:spring-shell-dependencies", UsedVersions.springShellVersion))
        put("spring.cloud.bom", library("org.springframework.cloud:spring-cloud-dependencies", UsedVersions.springCloudVersion))
        put("spring.cloud.bom.azure", library("com.azure.spring:spring-cloud-azure-dependencies", UsedVersions.springCloudAzureVersion))
        put("spring.cloud.bom.gcp", library("com.google.cloud:spring-cloud-gcp-dependencies", UsedVersions.springCloudGcpVersion))
        put("spring.cloud.bom.services", library("io.pivotal.spring.cloud:spring-cloud-services-dependencies", UsedVersions.springCloudServicesVersion))
        put("spring.ai.bom", library("org.springframework.ai:spring-ai-bom", UsedVersions.springAiVersion))
        put("spring.ai.bom.timefold", library("ai.timefold.solver:timefold-solver-bom", UsedVersions.springAiTimeFoldVersion))
        
        // Generic starters
        put("spring.boot.starter", library("org.springframework.boot:spring-boot-starter"))
        put("spring.boot.starter.test", library("org.springframework.boot:spring-boot-starter-test"))
        put("spring.boot.starter.actuator", library("org.springframework.boot:spring-boot-starter-actuator"))
        put("spring.boot.starter.validation", library("org.springframework.boot:spring-boot-starter-validation"))
        put("spring.boot.starter.cache", library("org.springframework.boot:spring-boot-starter-cache"))
        
        // Web
        put("spring.boot.starter.web", library("org.springframework.boot:spring-boot-starter-web"))
        put("spring.boot.starter.webflux", library("org.springframework.boot:spring-boot-starter-webflux"))
        put("spring.boot.starter.websocket", library("org.springframework.boot:spring-boot-starter-websocket"))
        put("spring.boot.starter.web.services", library("org.springframework.boot:spring-boot-starter-web-services"))
        put("spring.boot.starter.jersey", library("org.springframework.boot:spring-boot-starter-jersey"))
        put("spring.boot.starter.hateoas", library("org.springframework.boot:spring-boot-starter-hateoas"))
        put("spring.boot.starter.graphql", library("org.springframework.boot:spring-boot-starter-graphql"))
        put("spring.boot.starter.rsocket", library("org.springframework.boot:spring-boot-starter-rsocket"))

        // Security
        put("spring.boot.starter.security", library("org.springframework.boot:spring-boot-starter-security"))
        put("spring.boot.starter.oauth2.client", library("org.springframework.boot:spring-boot-starter-oauth2-client"))
        put("spring.boot.starter.oauth2.resourceServer", library("org.springframework.boot:spring-boot-starter-oauth2-resource-server"))
        put("spring.boot.starter.oauth2.authorizationServer", library("org.springframework.boot:spring-boot-starter-oauth2-authorization-server"))

        // Templates
        put("spring.boot.starter.thymeleaf", library("org.springframework.boot:spring-boot-starter-thymeleaf"))
        put("spring.boot.starter.freemarker", library("org.springframework.boot:spring-boot-starter-freemarker"))
        put("spring.boot.starter.mustache", library("org.springframework.boot:spring-boot-starter-mustache"))
        put("spring.boot.starter.groovyTemplates", library("org.springframework.boot:spring-boot-starter-groovy-templates"))

        // Data
        put("spring.boot.starter.data.jpa", library("org.springframework.boot:spring-boot-starter-data-jpa"))
        put("spring.boot.starter.data.jdbc", library("org.springframework.boot:spring-boot-starter-data-jdbc"))
        put("spring.boot.starter.data.r2dbc", library("org.springframework.boot:spring-boot-starter-data-r2dbc"))
        put("spring.boot.starter.jdbc", library("org.springframework.boot:spring-boot-starter-jdbc"))
        put("spring.boot.starter.data.elasticsearch", library("org.springframework.boot:spring-boot-starter-data-elasticsearch"))
        put("spring.boot.starter.data.mongodb", library("org.springframework.boot:spring-boot-starter-data-mongodb"))
        put("spring.boot.starter.data.mongodb.reactive", library("org.springframework.boot:spring-boot-starter-data-mongodb-reactive"))
        put("spring.boot.starter.data.neo4j", library("org.springframework.boot:spring-boot-starter-data-neo4j"))
        put("spring.boot.starter.data.redis", library("org.springframework.boot:spring-boot-starter-data-redis"))
        put("spring.boot.starter.data.redis.reactive", library("org.springframework.boot:spring-boot-starter-data-redis-reactive"))
        put("spring.boot.starter.data.cassandra", library("org.springframework.boot:spring-boot-starter-data-cassandra"))
        put("spring.boot.starter.data.cassandra.reactive", library("org.springframework.boot:spring-boot-starter-data-cassandra-reactive"))
        put("spring.boot.starter.data.couchbase", library("org.springframework.boot:spring-boot-starter-data-couchbase"))
        put("spring.boot.starter.data.couchbase.reactive", library("org.springframework.boot:spring-boot-starter-data-couchbase-reactive"))
        put("spring.boot.starter.data.ldap", library("org.springframework.boot:spring-boot-starter-data-ldap"))
        put("spring.boot.starter.data.rest", library("org.springframework.boot:spring-boot-starter-data-rest"))
        
        // We don't support jooq yet.
        // put("spring.boot.starter.jooq", library("org.springframework.boot:spring-boot-starter-jooq"))

        // Messaging
        put("spring.boot.starter.activemq", library("org.springframework.boot:spring-boot-starter-activemq"))
        put("spring.boot.starter.amqp", library("org.springframework.boot:spring-boot-starter-amqp"))
        put("spring.boot.starter.artemis", library("org.springframework.boot:spring-boot-starter-artemis"))
        put("spring.boot.starter.integration", library("org.springframework.boot:spring-boot-starter-integration"))
        put("spring.boot.starter.quartz", library("org.springframework.boot:spring-boot-starter-quartz"))
        put("spring.boot.starter.batch", library("org.springframework.boot:spring-boot-starter-batch"))
        put("spring.boot.starter.mail", library("org.springframework.boot:spring-boot-starter-mail"))
        put("spring.boot.starter.pulsar", library("org.springframework.boot:spring-boot-starter-pulsar"))
        put("spring.boot.starter.pulsar.reactive", library("org.springframework.boot:spring-boot-starter-pulsar-reactive"))

        // Devtools
        put("spring.boot.devtools", library("org.springframework.boot:spring-boot-devtools"))
        put("spring.boot.configuration.processor", library("org.springframework.boot:spring-boot-configuration-processor"))
        put("spring.boot.docker.compose", library("org.springframework.boot:spring-boot-docker-compose"))
        put("spring.boot.testcontainers", library("org.springframework.boot:spring-boot-testcontainers"))

        // Database drivers
        put("db.h2", library("com.h2database:h2"))
        put("db.mysql", library("com.mysql:mysql-connector-j"))
        put("db.postgresql", library("org.postgresql:postgresql"))
        put("db.mariadb", library("org.mariadb.jdbc:mariadb-java-client"))
        put("db.mssql", library("com.microsoft.sqlserver:mssql-jdbc"))
        put("db.oracle", library("com.oracle.database.jdbc:ojdbc11"))
        put("db.db2", library("com.ibm.db2:jcc"))
        put("db.derby", library("org.apache.derby:derby"))
        put("db.hsql", library("org.hsqldb:hsqldb"))

        // Observability
        put("micrometer.tracing", library("io.micrometer:micrometer-tracing-bridge-brave"))
        put("micrometer.prometheus", library("io.micrometer:micrometer-registry-prometheus"))
        put("micrometer.datadog", library("io.micrometer:micrometer-registry-datadog"))
        put("micrometer.graphite", library("io.micrometer:micrometer-registry-graphite"))
        put("micrometer.new.relic", library("io.micrometer:micrometer-registry-new-relic"))
        put("micrometer.wavefront", library("io.micrometer:micrometer-registry-wavefront"))
        put("micrometer.dynatrace", library("io.micrometer:micrometer-registry-dynatrace"))
        put("micrometer.influx", library("io.micrometer:micrometer-registry-influx"))
        put("micrometer.otlp", library("io.micrometer:micrometer-registry-otlp"))

        // Spring Cloud
        put("spring.cloud.config.client", library("org.springframework.cloud:spring-cloud-starter-config"))
        put("spring.cloud.config.server", library("org.springframework.cloud:spring-cloud-config-server"))
        put("spring.cloud.eureka.client", library("org.springframework.cloud:spring-cloud-starter-netflix-eureka-client"))
        put("spring.cloud.eureka.server", library("org.springframework.cloud:spring-cloud-starter-netflix-eureka-server"))
        put("spring.cloud.gateway.mvc", library("org.springframework.cloud:spring-cloud-starter-gateway-mvc"))
        put("spring.cloud.gateway", library("org.springframework.cloud:spring-cloud-starter-gateway"))
        put("spring.cloud.openfeign", library("org.springframework.cloud:spring-cloud-starter-openfeign"))
        put("spring.cloud.loadbalancer", library("org.springframework.cloud:spring-cloud-starter-loadbalancer"))
        put("spring.cloud.resilience4j", library("org.springframework.cloud:spring-cloud-starter-circuitbreaker-resilience4j"))
        put("spring.cloud.stream", library("org.springframework.cloud:spring-cloud-stream"))
        put("spring.cloud.task", library("org.springframework.cloud:spring-cloud-starter-task"))
        put("spring.cloud.bus", library("org.springframework.cloud:spring-cloud-bus"))
        put("spring.cloud.function", library("org.springframework.cloud:spring-cloud-function-context"))
        put("spring.cloud.vault", library("org.springframework.cloud:spring-cloud-starter-vault-config"))
        put("spring.cloud.zookeeper.config", library("org.springframework.cloud:spring-cloud-starter-zookeeper-config"))
        put("spring.cloud.zookeeper.discovery", library("org.springframework.cloud:spring-cloud-starter-zookeeper-discovery"))
        put("spring.cloud.consul.config", library("org.springframework.cloud:spring-cloud-starter-consul-config"))
        put("spring.cloud.consul.discovery", library("org.springframework.cloud:spring-cloud-starter-consul-discovery"))

        // Azure
        put("spring.cloud.azure.support", library("com.azure.spring:spring-cloud-azure-starter"))
        put("spring.cloud.azure.keyvault", library("com.azure.spring:spring-cloud-azure-starter-keyvault"))
        put("spring.cloud.azure.activeDirectory", library("com.azure.spring:spring-cloud-azure-starter-active-directory"))
        put("spring.cloud.azure.cosmos", library("com.azure.spring:spring-cloud-azure-starter-data-cosmos"))
        put("spring.cloud.azure.storage", library("com.azure.spring:spring-cloud-azure-starter-storage"))

        // Google Cloud
        put("spring.cloud.gcp", library("com.google.cloud:spring-cloud-gcp-starter"))
        put("spring.cloud.gcp.pubsub", library("com.google.cloud:spring-cloud-gcp-starter-pubsub"))
        put("spring.cloud.gcp.storage", library("com.google.cloud:spring-cloud-gcp-starter-storage"))

        // Spring AI
        put("spring.ai.openai", library("org.springframework.ai:spring-ai-openai-spring-boot-starter"))
        put("spring.ai.azure.openai", library("org.springframework.ai:spring-ai-azure-openai-spring-boot-starter"))
        put("spring.ai.ollama", library("org.springframework.ai:spring-ai-ollama-spring-boot-starter"))
        put("spring.ai.mistral", library("org.springframework.ai:spring-ai-mistral-ai-spring-boot-starter"))
        put("spring.ai.anthropic", library("org.springframework.ai:spring-ai-anthropic-spring-boot-starter"))
        put("spring.ai.bedrock", library("org.springframework.ai:spring-ai-bedrock-ai-spring-boot-starter"))
        put("spring.ai.vertexai.gemini", library("org.springframework.ai:spring-ai-vertex-ai-gemini-spring-boot-starter"))
        put("spring.ai.stabilityai", library("org.springframework.ai:spring-ai-stability-ai-spring-boot-starter"))

        put("spring.timefold.solver", library("ai.timefold.solver:timefold-solver-spring-boot-starter"))

        // Spring AI Vector Stores
        put("spring.ai.vectorstore.pgvector", library("org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter"))
        put("spring.ai.vectorstore.redis", library("org.springframework.ai:spring-ai-redis-store-spring-boot-starter"))
        put("spring.ai.vectorstore.milvus", library("org.springframework.ai:spring-ai-milvus-store-spring-boot-starter"))
        put("spring.ai.vectorstore.elasticsearch", library("org.springframework.ai:spring-ai-elasticsearch-store-spring-boot-starter"))
        put("spring.ai.vectorstore.pinecone", library("org.springframework.ai:spring-ai-pinecone-store-spring-boot-starter"))
        put("spring.ai.vectorstore.weaviate", library("org.springframework.ai:spring-ai-weaviate-store-spring-boot-starter"))
        put("spring.ai.vectorstore.chroma", library("org.springframework.ai:spring-ai-chroma-store-spring-boot-starter"))
        put("spring.ai.vectorstore.qdrant", library("org.springframework.ai:spring-ai-qdrant-store-spring-boot-starter"))
        put("spring.ai.vectorstore.neo4j", library("org.springframework.ai:spring-ai-neo4j-store-spring-boot-starter"))

        // Spring AI Document Readers
        put("spring.ai.pdf.document.reader", library("org.springframework.ai:spring-ai-pdf-document-reader"))
        put("spring.ai.markdown.document.reader", library("org.springframework.ai:spring-ai-markdown-document-reader"))
        put("spring.ai.tika.document.reader", library("org.springframework.ai:spring-ai-tika-document-reader"))

        // Admin components
        put("spring.boot.admin.client.codecentric", library("de.codecentric:spring-boot-admin-starter-client"))
        put("spring.boot.admin.server.codecentric", library("de.codecentric:spring-boot-admin-starter-server"))

        // Advanced capabilities
        put("spring.modulith", library("org.springframework.modulith:spring-modulith-starter-core"))
        put("spring.shell", library("org.springframework.shell:spring-shell-starter"))

        // We don't support grpc yet.
        // put("spring.grpc", library("org.springframework.grpc:spring-grpc-spring-boot-starter"))

        // Other frameworks and libraries
        put("spring.kafka", library("org.springframework.kafka:spring-kafka"))
        put("spring.session", library("org.springframework.session:spring-session-core"))
        put("spring.mybatis", library("org.mybatis.spring.boot:mybatis-spring-boot-starter"))
        put("spring.restdocs", library("org.springframework.restdocs:spring-restdocs-mockmvc"))
        
        put("lombok", library("org.projectlombok:lombok"))
        put("liquibase", library("org.liquibase:liquibase-core"))
        put("flyway", library("org.flywaydb:flyway-core"))
    }
    // @formatter:on
}) {
    init {
        entries.forEach {
            it.value.trace = (it.value.trace as? BuiltinCatalogTrace)?.copy(catalog = this)
        }
    }
}

fun library(groupAndModule: String, version: TraceableString): TraceableString =
    TraceableString("$groupAndModule:${version.value}").apply {
        trace = BuiltinCatalogTrace(EmptyCatalog, computedValueTrace = version)
    }

fun library(groupAndModule: String, version: String): TraceableString =
    TraceableString("$groupAndModule:$version").apply { trace = BuiltinCatalogTrace(EmptyCatalog, null) }

fun library(groupAndModule: String): TraceableString =
    TraceableString(groupAndModule).apply { trace = BuiltinCatalogTrace(EmptyCatalog, null) }

private object EmptyCatalog : VersionCatalog {
    override val entries: Map<String, TraceableString> = emptyMap()
    override val isPhysical: Boolean = false
    override fun findInCatalog(key: String): TraceableString? = null
}

private fun materialIconsVersion(composeVersion: TraceableString) =
    when {
        ComparableVersion(composeVersion.value) >= ComparableVersion("1.8.0") -> TraceableString("1.7.3")
        else -> composeVersion
    }
