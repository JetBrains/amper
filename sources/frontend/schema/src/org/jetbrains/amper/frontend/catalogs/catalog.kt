/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.catalogs

import org.apache.maven.artifact.versioning.ComparableVersion
import org.jetbrains.amper.frontend.InMemoryVersionCatalog
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.BuiltinCatalogTrace
import org.jetbrains.amper.frontend.api.DefaultTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.jetbrains.amper.frontend.api.TraceableVersion
import org.jetbrains.amper.frontend.api.schemaDelegate
import org.jetbrains.amper.frontend.asBuildProblemSource
import org.jetbrains.amper.frontend.reportBundleError
import org.jetbrains.amper.frontend.schema.DefaultVersions
import org.jetbrains.amper.frontend.schema.Settings
import org.jetbrains.amper.problems.reporting.NonIdealDiagnostic
import org.jetbrains.amper.problems.reporting.ProblemReporter
import org.jetbrains.amper.system.info.SystemInfo
import kotlin.reflect.KProperty0

context(problemReporter: ProblemReporter)
internal fun Settings.builtInCatalog(): VersionCatalog = BuiltInCatalog(
    kotlinVersion = kotlin::version.asTraceableVersion(DefaultVersions.kotlin),
    serializationVersion = kotlin.serialization::version.asTraceableVersion(DefaultVersions.kotlinxSerialization)
        .takeIf { kotlin.serialization.enabled },
    rpcVersion = kotlin.rpc::version.asTraceableVersion(DefaultVersions.kotlinxRpc)
        .takeIf { kotlin.rpc.enabled },
    composeVersion = compose::version.asTraceableVersion(DefaultVersions.compose)
        .takeIf { compose.enabled },
    ktorVersion = ktor::version.asTraceableVersion(DefaultVersions.ktor)
        .takeIf { ktor.enabled },
    springBootVersion = springBoot::version.asTraceableVersion(DefaultVersions.springBoot)
        .takeIf { springBoot.enabled },
    composeHotReloadVersion = compose.experimental.hotReload::version
        .asTraceableVersion(DefaultVersions.composeHotReload),
)

context(problemReporter: ProblemReporter)
@OptIn(NonIdealDiagnostic::class)
private fun KProperty0<String>.asTraceableVersion(fallbackVersion: String): TraceableVersion {
    // we validate the version only for emptiness because maven artifacts allow any string as a version
    //  that's why we cannot provide a precise validation for non-empty strings
    return if (!schemaDelegate.value.isEmpty()) {
        TraceableVersion(schemaDelegate.value, schemaDelegate.trace)
    } else {
        problemReporter.reportBundleError(
            source = schemaDelegate.trace.asBuildProblemSource(),
            messageKey = "empty.version.string",
        )
        // TODO instead of this fallback, we could add a general @NonEmpty validation up front, so the schema
        //  instantiator deals with it before creating invalid schema elements. Invalid values are already replaced
        //  with their defaults for best effort handling, so we should end up with the same result, but without any
        //  special handling here, and the trace will be a real default.
        // fallback to avoid double errors
        TraceableVersion(fallbackVersion, trace = DefaultTrace)
    }
}

private class BuiltInCatalog(
    kotlinVersion: TraceableVersion,
    serializationVersion: TraceableVersion?,
    rpcVersion: TraceableVersion?,
    composeVersion: TraceableVersion?,
    ktorVersion: TraceableVersion?,
    springBootVersion: TraceableVersion?,
    composeHotReloadVersion: TraceableVersion,
    private val systemInfo: SystemInfo = SystemInfo.CurrentHost,
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

        if (rpcVersion != null) {
            put("kotlin.rpc.bom", library("org.jetbrains.kotlinx:kotlinx-rpc-bom", rpcVersion))
            put("kotlin.rpc.core", library("org.jetbrains.kotlinx:kotlinx-rpc-core", rpcVersion))
            put("kotlin.rpc.krpc.client", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-client", rpcVersion))
            put("kotlin.rpc.krpc.core", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-core", rpcVersion))
            put("kotlin.rpc.krpc.ktor.client", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-client", rpcVersion))
            put("kotlin.rpc.krpc.ktor.core", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-core", rpcVersion))
            put("kotlin.rpc.krpc.ktor.server", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-ktor-server", rpcVersion))
            put("kotlin.rpc.krpc.serialization.cbor", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-cbor", rpcVersion))
            put("kotlin.rpc.krpc.serialization.core", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-core", rpcVersion))
            put("kotlin.rpc.krpc.serialization.json", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-json", rpcVersion))
            put("kotlin.rpc.krpc.serialization.protobuf", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-serialization-protobuf", rpcVersion))
            put("kotlin.rpc.krpc.server", library("org.jetbrains.kotlinx:kotlinx-rpc-krpc-server", rpcVersion))
        }

        // Add compose.
        if (composeVersion != null) {
            put("compose.animation", library("org.jetbrains.compose.animation:animation", composeVersion))
            put("compose.animationGraphics", library("org.jetbrains.compose.animation:animation-graphics", composeVersion))
            put("compose.components.resources", library("org.jetbrains.compose.components:components-resources", composeVersion))
            put("compose.desktop.common", library("org.jetbrains.compose.desktop:desktop", composeVersion))
            put("compose.desktop.components.animatedImage", library("org.jetbrains.compose.components:components-animatedimage", composeVersion))
            put("compose.desktop.components.splitPane", library("org.jetbrains.compose.components:components-splitpane", composeVersion))
            put("compose.desktop.currentOs", library("org.jetbrains.compose.desktop:desktop-jvm-${systemInfo.familyArch}", composeVersion))
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
            // material icons is no longer
            if (ComparableVersion(composeVersion.value) < ComparableVersion("1.8.0")) {
                put("compose.materialIconsCore", library("org.jetbrains.compose.material:material-icons-core", composeVersion))
                put("compose.materialIconsExtended", library("org.jetbrains.compose.material:material-icons-extended", composeVersion))
            }
            put("compose.preview", library("org.jetbrains.compose.ui:ui-tooling-preview", composeVersion))
            put("compose.preview", library("org.jetbrains.compose.ui:ui-tooling-preview", composeVersion))
            put("compose.runtime", library("org.jetbrains.compose.runtime:runtime", composeVersion))
            put("compose.runtimeSaveable", library("org.jetbrains.compose.runtime:runtime-saveable", composeVersion))
            put("compose.ui", library("org.jetbrains.compose.ui:ui", composeVersion))
            put("compose.uiTest", library("org.jetbrains.compose.ui:ui-test", composeVersion))
            put("compose.uiTooling", library("org.jetbrains.compose.ui:ui-tooling", composeVersion))

            put("compose.hotReload.runtimeApi", library("org.jetbrains.compose.hot-reload:hot-reload-runtime-api", composeHotReloadVersion))
        }

        // add ktor
        if (ktorVersion != null) {
            // bom
            put("ktor.bom", library("io.ktor:ktor-bom", ktorVersion))

            // server
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
            put("ktor.server.testHost", library("io.ktor:ktor-server-test-host", ktorVersion))
            put("ktor.server.thymeleaf", library("io.ktor:ktor-server-thymeleaf", ktorVersion))
            put("ktor.server.tomcat", library("io.ktor:ktor-server-tomcat", ktorVersion))
            put("ktor.server.tomcat.jakarta", library("io.ktor:ktor-server-tomcat-jakarta", ktorVersion))
            put("ktor.server.velocity", library("io.ktor:ktor-server-velocity", ktorVersion))
            put("ktor.server.webjars", library("io.ktor:ktor-server-webjars", ktorVersion))
            put("ktor.server.websockets", library("io.ktor:ktor-server-websockets", ktorVersion))
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

        // Spring Boot dependencies
        if (springBootVersion != null) {
            put("spring.boot", library("org.springframework.boot:spring-boot", springBootVersion))
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
            put("spring.boot.starter.data.neo4j", library("org.springframework.boot:spring-boot-starter-data-neo4j", springBootVersion))
            put("spring.boot.starter.data.r2dbc", library("org.springframework.boot:spring-boot-starter-data-r2dbc", springBootVersion))
            put("spring.boot.starter.data.redis", library("org.springframework.boot:spring-boot-starter-data-redis", springBootVersion))
            put("spring.boot.starter.data.redis.reactive", library("org.springframework.boot:spring-boot-starter-data-redis-reactive", springBootVersion))
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
            put("spring.boot.starter.test.data.jpa", library("org.springframework.boot:spring-boot-starter-data-jpa-test", springBootVersion))
            put("spring.boot.starter.test.jdbc", library("org.springframework.boot:spring-boot-starter-jdbc-test", springBootVersion))
            put("spring.boot.starter.test.restclient", library("org.springframework.boot:spring-boot-starter-restclient-test", springBootVersion))
            put("spring.boot.starter.test.webmvc", library("org.springframework.boot:spring-boot-starter-webmvc-test", springBootVersion))
            put("spring.boot.starter.thymeleaf", library("org.springframework.boot:spring-boot-starter-thymeleaf", springBootVersion))
            put("spring.boot.starter.tomcat", library("org.springframework.boot:spring-boot-starter-tomcat", springBootVersion))
            put("spring.boot.starter.undertow", library("org.springframework.boot:spring-boot-starter-undertow", springBootVersion))
            put("spring.boot.starter.validation", library("org.springframework.boot:spring-boot-starter-validation", springBootVersion))
            put("spring.boot.starter.web", library("org.springframework.boot:spring-boot-starter-web", springBootVersion))
            put("spring.boot.starter.web.services", library("org.springframework.boot:spring-boot-starter-web-services", springBootVersion))
            put("spring.boot.starter.webflux", library("org.springframework.boot:spring-boot-starter-webflux", springBootVersion))
            put("spring.boot.starter.webmvc", library("org.springframework.boot:spring-boot-starter-webmvc", springBootVersion))
            put("spring.boot.starter.websocket", library("org.springframework.boot:spring-boot-starter-websocket", springBootVersion))
            put("spring.boot.test", library("org.springframework.boot:spring-boot-test", springBootVersion))
            put("spring.boot.test.autoconfigure", library("org.springframework.boot:spring-boot-test-autoconfigure", springBootVersion))
            put("spring.boot.testcontainers", library("org.springframework.boot:spring-boot-testcontainers", springBootVersion))
        }
        // @formatter:on
    }
}

context(catalog: VersionCatalog)
private fun library(groupAndModule: String, version: TraceableVersion): TraceableString =
    TraceableString(
        value = "$groupAndModule:${version.value}",
        trace = BuiltinCatalogTrace(catalog, version = version),
    )
