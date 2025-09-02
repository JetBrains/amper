/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.core.UsedVersions
import org.jetbrains.amper.core.UsedVersions.logbackVersion
import org.jetbrains.amper.core.system.DefaultSystemInfo
import org.jetbrains.amper.core.system.SystemInfo
import org.jetbrains.amper.frontend.InMemoryVersionCatalog
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableVersion
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter

context(problemReporter: ProblemReporter)
internal fun Settings.builtInCatalog(): VersionCatalog = BuiltInCatalog(
    kotlinVersion = version(
        version = TraceableVersion(kotlin.version, kotlin::version.schemaDelegate),
        fallbackVersion = UsedVersions.defaultKotlinVersion,
    ),
    serializationVersion = kotlin.serialization.takeIf { it.enabled }?.version
        ?.let { TraceableVersion(it, kotlin.serialization::version.schemaDelegate) }
        ?.let { version(it, UsedVersions.kotlinxSerializationVersion) },
    composeVersion = compose.takeIf { it.enabled }?.version
        ?.let { TraceableVersion(it, compose::version.schemaDelegate) }
        ?.let { version(it, UsedVersions.composeVersion) },
    ktorVersion = ktor.takeIf { it.enabled }?.version
        ?.let { TraceableVersion(it, ktor::version.schemaDelegate) }
        ?.let { version(it, UsedVersions.ktorVersion) },
    springBootVersion = springBoot.takeIf { it.enabled }?.version
        ?.let { TraceableVersion(it, springBoot::version.schemaDelegate) }
        ?.let { version(it, UsedVersions.springBootVersion) },
)

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
        TraceableString(fallbackVersion, trace = DefaultTrace)
    }
}

private class BuiltInCatalog(
    kotlinVersion: TraceableString,
    serializationVersion: TraceableString?,
    composeVersion: TraceableString?,
    ktorVersion: TraceableString?,
    springBootVersion: TraceableString?,
    private val systemInfo: SystemInfo = DefaultSystemInfo,
) : InMemoryVersionCatalog {

    override val entries: Map<String, TraceableString> = buildMap {
        // @formatter:off
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
            put("spring.boot", library("org.springframework.boot:spring-boot", springBootVersion))
            put("spring.boot.test", library("org.springframework.boot:spring-boot-test", springBootVersion))
            put("spring.boot.test.autoconfigure", library("org.springframework.boot:spring-boot-test-autoconfigure", springBootVersion))
            put("spring.boot.testcontainers", library("org.springframework.boot:spring-boot-testcontainers", springBootVersion))
            put("spring.boot.actuator", library("org.springframework.boot:spring-boot-actuator", springBootVersion))
            put("spring.boot.actuator.autoconfigure", library("org.springframework.boot:spring-boot-actuator-autoconfigure", springBootVersion))
            put("spring.boot.autoconfigure", library("org.springframework.boot:spring-boot-autoconfigure", springBootVersion))
            put("spring.boot.autoconfigure.processor", library("org.springframework.boot:spring-boot-autoconfigure-processor", springBootVersion))
            put("spring.boot.buildpack.platform", library("org.springframework.boot:spring-boot-buildpack-platform", springBootVersion))
            put("spring.boot.configuration.metadata", library("org.springframework.boot:spring-boot-configuration-metadata", springBootVersion))
            put("spring.boot.configuration.processor", library("org.springframework.boot:spring-boot-configuration-processor", springBootVersion))
            put("spring.boot.devtools", library("org.springframework.boot:spring-boot-devtools", springBootVersion))
            put("spring.boot.docker.compose", library("org.springframework.boot:spring-boot-docker-compose", springBootVersion))
            put("spring.boot.jarmode.tools", library("org.springframework.boot:spring-boot-jarmode-tools", springBootVersion))
            put("spring.boot.loader", library("org.springframework.boot:spring-boot-loader", springBootVersion))
            put("spring.boot.loader.classic", library("org.springframework.boot:spring-boot-loader-classic", springBootVersion))
            put("spring.boot.loader.tools", library("org.springframework.boot:spring-boot-loader-tools", springBootVersion))
            put("spring.boot.properties.migrator", library("org.springframework.boot:spring-boot-properties-migrator", springBootVersion))
            put("spring.boot.starter", library("org.springframework.boot:spring-boot-starter", springBootVersion))
            put("spring.boot.starter.activemq", library("org.springframework.boot:spring-boot-starter-activemq", springBootVersion))
            put("spring.boot.starter.actuator", library("org.springframework.boot:spring-boot-starter-actuator", springBootVersion))
            put("spring.boot.starter.amqp", library("org.springframework.boot:spring-boot-starter-amqp", springBootVersion))
            put("spring.boot.starter.aop", library("org.springframework.boot:spring-boot-starter-aop", springBootVersion))
            put("spring.boot.starter.artemis", library("org.springframework.boot:spring-boot-starter-artemis", springBootVersion))
            put("spring.boot.starter.batch", library("org.springframework.boot:spring-boot-starter-batch", springBootVersion))
            put("spring.boot.starter.cache", library("org.springframework.boot:spring-boot-starter-cache", springBootVersion))
            put("spring.boot.starter.data.cassandra", library("org.springframework.boot:spring-boot-starter-data-cassandra", springBootVersion))
            put("spring.boot.starter.data.cassandra.reactive", library("org.springframework.boot:spring-boot-starter-data-cassandra-reactive", springBootVersion))
            put("spring.boot.starter.data.couchbase", library("org.springframework.boot:spring-boot-starter-data-couchbase", springBootVersion))
            put("spring.boot.starter.data.couchbase.reactive", library("org.springframework.boot:spring-boot-starter-data-couchbase-reactive", springBootVersion))
            put("spring.boot.starter.data.elasticsearch", library("org.springframework.boot:spring-boot-starter-data-elasticsearch", springBootVersion))
            put("spring.boot.starter.data.jdbc", library("org.springframework.boot:spring-boot-starter-data-jdbc", springBootVersion))
            put("spring.boot.starter.data.jpa", library("org.springframework.boot:spring-boot-starter-data-jpa", springBootVersion))
            put("spring.boot.starter.data.ldap", library("org.springframework.boot:spring-boot-starter-data-ldap", springBootVersion))
            put("spring.boot.starter.data.mongodb", library("org.springframework.boot:spring-boot-starter-data-mongodb", springBootVersion))
            put("spring.boot.starter.data.mongodb.reactive", library("org.springframework.boot:spring-boot-starter-data-mongodb-reactive", springBootVersion))
            put("spring.boot.starter.data.r2dbc", library("org.springframework.boot:spring-boot-starter-data-r2dbc", springBootVersion))
            put("spring.boot.starter.data.redis", library("org.springframework.boot:spring-boot-starter-data-redis", springBootVersion))
            put("spring.boot.starter.data.redis.reactive", library("org.springframework.boot:spring-boot-starter-data-redis-reactive", springBootVersion))
            put("spring.boot.starter.data.neo4j", library("org.springframework.boot:spring-boot-starter-data-neo4j", springBootVersion))
            put("spring.boot.starter.data.rest", library("org.springframework.boot:spring-boot-starter-data-rest", springBootVersion))
            put("spring.boot.starter.freemarker", library("org.springframework.boot:spring-boot-starter-freemarker", springBootVersion))
            put("spring.boot.starter.graphql", library("org.springframework.boot:spring-boot-starter-graphql", springBootVersion))
            put("spring.boot.starter.groovy.templates", library("org.springframework.boot:spring-boot-starter-groovy-templates", springBootVersion))
            put("spring.boot.starter.hateoas", library("org.springframework.boot:spring-boot-starter-hateoas", springBootVersion))
            put("spring.boot.starter.integration", library("org.springframework.boot:spring-boot-starter-integration", springBootVersion))
            put("spring.boot.starter.jdbc", library("org.springframework.boot:spring-boot-starter-jdbc", springBootVersion))
            put("spring.boot.starter.jersey", library("org.springframework.boot:spring-boot-starter-jersey", springBootVersion))
            put("spring.boot.starter.jetty", library("org.springframework.boot:spring-boot-starter-jetty", springBootVersion))
            put("spring.boot.starter.jooq", library("org.springframework.boot:spring-boot-starter-jooq", springBootVersion))
            put("spring.boot.starter.json", library("org.springframework.boot:spring-boot-starter-json", springBootVersion))
            put("spring.boot.starter.log4j2", library("org.springframework.boot:spring-boot-starter-log4j2", springBootVersion))
            put("spring.boot.starter.logging", library("org.springframework.boot:spring-boot-starter-logging", springBootVersion))
            put("spring.boot.starter.mail", library("org.springframework.boot:spring-boot-starter-mail", springBootVersion))
            put("spring.boot.starter.mustache", library("org.springframework.boot:spring-boot-starter-mustache", springBootVersion))
            put("spring.boot.starter.oauth2.authorization.server", library("org.springframework.boot:spring-boot-starter-oauth2-authorization-server", springBootVersion))
            put("spring.boot.starter.oauth2.client", library("org.springframework.boot:spring-boot-starter-oauth2-client", springBootVersion))
            put("spring.boot.starter.oauth2.resource.server", library("org.springframework.boot:spring-boot-starter-oauth2-resource-server", springBootVersion))
            put("spring.boot.starter.pulsar", library("org.springframework.boot:spring-boot-starter-pulsar", springBootVersion))
            put("spring.boot.starter.pulsar.reactive", library("org.springframework.boot:spring-boot-starter-pulsar-reactive", springBootVersion))
            put("spring.boot.starter.quartz", library("org.springframework.boot:spring-boot-starter-quartz", springBootVersion))
            put("spring.boot.starter.reactor.netty", library("org.springframework.boot:spring-boot-starter-reactor-netty", springBootVersion))
            put("spring.boot.starter.rsocket", library("org.springframework.boot:spring-boot-starter-rsocket", springBootVersion))
            put("spring.boot.starter.security", library("org.springframework.boot:spring-boot-starter-security", springBootVersion))
            put("spring.boot.starter.test", library("org.springframework.boot:spring-boot-starter-test", springBootVersion))
            put("spring.boot.starter.thymeleaf", library("org.springframework.boot:spring-boot-starter-thymeleaf", springBootVersion))
            put("spring.boot.starter.tomcat", library("org.springframework.boot:spring-boot-starter-tomcat", springBootVersion))
            put("spring.boot.starter.undertow", library("org.springframework.boot:spring-boot-starter-undertow", springBootVersion))
            put("spring.boot.starter.validation", library("org.springframework.boot:spring-boot-starter-validation", springBootVersion))
            put("spring.boot.starter.web", library("org.springframework.boot:spring-boot-starter-web", springBootVersion))
            put("spring.boot.starter.webflux", library("org.springframework.boot:spring-boot-starter-webflux", springBootVersion))
            put("spring.boot.starter.websocket", library("org.springframework.boot:spring-boot-starter-websocket", springBootVersion))
            put("spring.boot.starter.web.services", library("org.springframework.boot:spring-boot-starter-web-services", springBootVersion))
        }
        // @formatter:on
    }
}

context(catalog: VersionCatalog)
private fun library(groupAndModule: String, version: TraceableString): TraceableString =
    TraceableString(
        value = "$groupAndModule:${version.value}",
        trace = BuiltinCatalogTrace(catalog, computedValueTrace = version),
    )

context(catalog: VersionCatalog)
private fun library(groupAndModule: String, version: String): TraceableString =
    TraceableString("$groupAndModule:$version", trace = BuiltinCatalogTrace(catalog))

context(catalog: VersionCatalog)
private fun library(groupAndModule: String): TraceableString =
    TraceableString(groupAndModule, trace = BuiltinCatalogTrace(catalog))

private fun materialIconsVersion(composeVersion: TraceableString) =
    when {
        ComparableVersion(composeVersion.value) >= ComparableVersion("1.8.0") ->
            TraceableString("1.7.3", DefaultTrace(computedValueTrace = composeVersion))
        else -> composeVersion
    }
