#!/usr/bin/env kotlin

/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:DependsOn("com.fasterxml.jackson.core:jackson-databind:2.19.2")
@file:DependsOn("com.fasterxml.jackson.module:jackson-module-kotlin:2.19.2")
@file:DependsOn("com.fasterxml.jackson.dataformat:jackson-dataformat-xml:2.19.2")

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import kotlin.collections.plus
import kotlin.io.path.absolute
import kotlin.io.path.div
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

// Configuration
val amperRootDir: Path = __FILE__.toPath().absolute().parent // __FILE__ is this script

val catalogKt = amperRootDir / "sources/frontend/schema/src/org/jetbrains/amper/frontend/catalogs/catalog.kt"
val syncVersionsMainKts = amperRootDir / "syncVersions.main.kts"

@JsonIgnoreProperties(ignoreUnknown = true)
data class SpringBootDependencies(
    @get:JsonProperty("boms")
    val boms: Map<String, Bom>,
) {
    data class Bom(
        @get:JsonProperty("groupId")
        val groupId: String,
        @get:JsonProperty("artifactId")
        val artifactId: String,
        @get:JsonProperty("version")
        val version: String,
    )
}

data class Pom(
    val dependencyManagement: DependencyManagement,
    val properties: Map<String, String>?,
    val version: String?,
) {
    data class DependencyManagement(val dependencies: List<Dependency>) {
        data class Dependency(val groupId: String, val artifactId: String, val version: String)
    }
}

data class AliasedDependency(val alias: String, val dependency: Pom.DependencyManagement.Dependency) {
    override fun toString(): String {
        return "$alias - ${dependency.groupId}:${dependency.artifactId}:${dependency.version}"
    }
}

val httpClient = HttpClient.newHttpClient()

fun fetchSpringBoms(springBootVersion: String = "3.5.4"): Map<String, SpringBootDependencies.Bom> {
    val request = HttpRequest.newBuilder(URI("https://start.spring.io/dependencies?$springBootVersion"))
        .GET()
        .build()

    val body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()
    val dependencies = jacksonObjectMapper().readValue(body, SpringBootDependencies::class.java)
    return dependencies.boms
}

fun fetchBom(groupId: String, artifactId: String, version: String): Pom {
    val request = HttpRequest.newBuilder(
        URI(
            "https://repo1.maven.org/maven2/${
                groupId.replace(
                    ".",
                    "/"
                )
            }/${artifactId}/${version}/${artifactId}-${version}.pom"
        )
    )
        .GET()
        .build()

    val body = httpClient.send(request, HttpResponse.BodyHandlers.ofString()).body()

    val xmlMapper = XmlMapper()
    xmlMapper.registerModule(KotlinModule.Builder().build())
    xmlMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    val pom = xmlMapper.readValue<Pom>(body)
    val projectVersion = pom.version ?: version
    return pom.copy(
        dependencyManagement = pom.dependencyManagement.copy(
            dependencies = pom.dependencyManagement.dependencies.map {
                it.copy(
                    version = if (it.version == $$"${project.version}") {
                        projectVersion
                    } else if (it.version.startsWith("$")) {
                        val property = it.version.substringAfter($$"${").substringBefore("}")
                        pom.properties?.get(property)?.let {
                            if (it == $$"${project.version}") {
                                projectVersion
                            } else {
                                it
                            }
                        } ?: it.version
                    } else {
                        it.version
                    }
                )
            }
        )
    )
}

fun dependencies(boms: Map<String, SpringBootDependencies.Bom>): List<Pom.DependencyManagement.Dependency> = buildList {
    boms.forEach { (name, bom) ->
        addAll(dependencies(bom))
    }
}

fun dependencies(boms: List<SpringBootDependencies.Bom>): List<Pom.DependencyManagement.Dependency> = buildList {
    boms.forEach { bom ->
        addAll(dependencies(bom))
    }
}

fun dependencies(bom: SpringBootDependencies.Bom): List<Pom.DependencyManagement.Dependency> {
    val pom = fetchBom(bom.groupId, bom.artifactId, bom.version)
    return pom.dependencyManagement.dependencies
}

fun getSpringBootVersionFromSyncVersions(): String {
    val syncVersionsContent = syncVersionsMainKts.readText()
    val regex = Regex("""val\s+springBootVersion\s*=\s*"([^"]+)"""")
    val matchResult = regex.find(syncVersionsContent)
    return matchResult?.groupValues?.get(1) ?: error("Could not find springBootVersion in syncVersions.main.kts")
}

fun main() {
    val springBootVersion = getSpringBootVersionFromSyncVersions()
    println("Using Spring Boot version $springBootVersion")
    val springBootBoms = fetchSpringBoms()

    val springCloudBoms = springBootBoms.entries.single { it.key == "spring-cloud" }.value
    val springCloudDependencies = dependencies(dependencies(springCloudBoms).map {
        SpringBootDependencies.Bom(
            it.groupId,
            it.artifactId,
            it.version
        )
    })

    val springBootBom = SpringBootDependencies.Bom(
        "org.springframework.boot",
        "spring-boot-dependencies",
        springBootVersion
    )
    val springBootDependencies = dependencies(springBootBom)
    val springSecurityDependency = springBootDependencies.single { it.artifactId == "spring-security-bom" }
    val springSecurityBom = SpringBootDependencies.Bom(
        springSecurityDependency.groupId,
        springSecurityDependency.artifactId,
        springSecurityDependency.version
    )
    val springSecurityDependencies = dependencies(springSecurityBom)

    val filteredSpringBoms = springBootBoms.filter {
        it.key !in setOf(
            "spring-cloud", // already handled
            "timefold-solver", // what is this?
            "spring-grpc" // isn't supported yet
        )
    }

    val dependencies =
        springBootDependencies + dependencies(filteredSpringBoms) + springCloudDependencies + springSecurityDependencies

    val stopList = setOf(
        "io.spring.gradle:dependency-management-plugin", // Gradle-related
        // Scala-related
        "org.apache.kafka:kafka_2.12",
        "org.apache.kafka:kafka_2.13",
        "org.apache.kafka.trogdor:kafka-trogdor", // seems like they've changed coordinates
    )

    val filteredDependencies = dependencies.filter { "${it.groupId}:${it.artifactId}" !in stopList }
    val deduplicatedDependencies = filteredDependencies.distinctBy { "${it.groupId}:${it.artifactId}" }

    val aliasedDependencies = deduplicatedDependencies.map { dependency ->
        AliasedDependency(
            alias = dependency.artifactId
                .replace("-", ".")
                .replace("_", "."),
            dependency = dependency
        )
    }

    val replacements = mapOf(
        "org.eclipse.angus:dsn" to "angus.dsn",
        "org.eclipse.angus:gimap" to "angus.gimap",
        "org.eclipse.angus:imap" to "angus.imap",
        "org.eclipse.angus:jakarta.mail" to "angus.jakarta.mail",
        "org.eclipse.angus:logging-mailhandler" to "angus.logging.mailhandler",
        "org.eclipse.angus:pop3" to "angus.pop3",
        "org.eclipse.angus:smtp" to "angus.smtp",

        "com.github.ben-manes.caffeine:guava" to "caffeine.guava",
        "com.github.ben-manes.caffeine:jcache" to "caffeine.jcache",
        "com.github.ben-manes.caffeine:simulator" to "caffeine.simulator",

        "org.apache.cassandra:java-driver-core" to "cassandra.java-driver-core",

        "com.fasterxml:classmate" to "fasterxml.classmate",

        "com.couchbase.client:java-client" to "couchbase.java.client",

        "com.ibm.db2:jcc" to "db2.jcc",
        "com.zaxxer:HikariCP" to "hikaricp",

        "org.apache.httpcomponents:httpasyncclient" to "apache.httpasyncclient",
        "org.apache.httpcomponents.client5:httpclient5" to "apache.httpclient5",
        "org.apache.httpcomponents.client5:httpclient5-cache" to "apache.httpclient5.cache",
        "org.apache.httpcomponents.client5:httpclient5-fluent" to "apache.httpclient5.fluent",
        "org.apache.httpcomponents:httpcore" to "apache.httpcore",
        "org.apache.httpcomponents:httpcore-nio" to "apache.httpcore.nio",
        "org.apache.httpcomponents.core5:httpcore5" to "apache.httpcore5",
        "org.apache.httpcomponents.core5:httpcore5-h2" to "apache.httpcore5.h2",
        "org.apache.httpcomponents.core5:httpcore5-reactive" to "apache.httpcore5.reactive",

        "org.codehaus.janino:commons-compiler" to "janino.commons.compiler",
        "org.codehaus.janino:commons-compiler-jdk" to "janino.commons.compiler.jdk",

        "javax.cache:cache-api" to "javax.cache.api",
        "javax.money:money-api" to "javax.money.api",

        "org.firebirdsql.jdbc:jaybird" to "firebirdsql.jdbc.jaybird",

        "org.apache.kafka:connect" to "kafka.connect",
        "org.apache.kafka:connect-api" to "kafka.connect.api",
        "org.apache.kafka:connect-basic-auth-extension" to "kafka.connect.basic.auth.extension",
        "org.apache.kafka:connect-file" to "kafka.connect.file",
        "org.apache.kafka:connect-json" to "kafka.connect.json",
        "org.apache.kafka:connect-mirror" to "kafka.connect.mirror",
        "org.apache.kafka:connect-mirror-client" to "kafka.connect.mirror.client",
        "org.apache.kafka:connect-runtime" to "kafka.connect.runtime",
        "org.apache.kafka:connect-transforms" to "kafka.connect.transforms",

        "org.apache.kafka:generator" to "kafka.generator",

        "com.oracle.database.ha:ons" to "oracle.ons",
        "com.oracle.database.ha:simplefan" to "oracle.simplefan",
        "com.oracle.database.jdbc:ojdbc11" to "oracle.ojdbc11",
        "com.oracle.database.jdbc:ojdbc11-production" to "oracle.ojdbc11.production",
        "com.oracle.database.jdbc:ojdbc17" to "oracle.ojdbc17",
        "com.oracle.database.jdbc:ojdbc17-production" to "oracle.ojdbc17.production",
        "com.oracle.database.jdbc:ojdbc8" to "oracle.ojdbc8",
        "com.oracle.database.jdbc:ojdbc8-production" to "oracle.ojdbc8.production",
        "com.oracle.database.jdbc:rsi" to "oracle.rsi",
        "com.oracle.database.jdbc:ucp" to "oracle.ucp",
        "com.oracle.database.jdbc:ucp11" to "oracle.ucp11",
        "com.oracle.database.jdbc:ucp17" to "oracle.ucp17",
        "com.oracle.database.nls:orai18n" to "oracle.orai18n",
        "com.oracle.database.security:oraclepki" to "oracle.oraclepki",
        "com.oracle.database.xml:xdb" to "oracle.xdb",
        "com.oracle.database.xml:xmlparserv2" to "oracle.xmlparserv2",
        "com.oracle.database.r2dbc:oracle-r2dbc" to "oracle.r2dbc",

        "com.rabbitmq:amqp-client" to "rabbitmq.amqp.client",
        "com.rabbitmq:stream-client" to "rabbitmq.stream.client",

        "org.apache.cassandra:java-driver-bom" to "cassandra.java.driver.bom",

        // Selenium HTMLUnit driver
        "org.seleniumhq.selenium:htmlunit3-driver" to "selenium.htmlunit3.driver",

        "org.skyscreamer:jsonassert" to "skyscreamer.jsonassert",
        "com.toomuchcoding.jsonassert:jsonassert" to "toomuchcoding.jsonassert",
        "com.toomuchcoding.jsonassert:jsonassert-shade" to "toomuchcoding.jsonassert.shade",

        "org.apache.kafka:kafka-clients" to "kafka.clients",
        "org.apache.kafka:kafka-log4j-appender" to "kafka.log4j.appender",
        "org.apache.kafka:kafka-metadata" to "kafka.metadata",
        "org.apache.kafka:kafka-raft" to "kafka.raft",
        "org.apache.kafka:kafka-server" to "kafka.server",
        "org.apache.kafka:kafka-server-common" to "kafka.server.common",
        "org.apache.kafka:kafka-shell" to "kafka.shell",
        "org.apache.kafka:kafka-storage" to "kafka.storage",
        "org.apache.kafka:kafka-storage-api" to "kafka.storage.api",
        "org.apache.kafka:kafka-streams" to "kafka.streams",
        "org.apache.kafka:kafka-streams-test-utils" to "kafka.streams.test.utils",
        "org.apache.kafka:kafka-tools" to "kafka.tools",
        "org.apache.kafka:trogdor" to "kafka.trogdor",

        "com.google.cloud:alloydb-jdbc-connector" to "google.cloud.alloydb.jdbc.connector",
        "com.google.cloud.sql:jdbc-socket-factory-core" to "google.cloud.sql.jdbc.socket.factory.core",
        "com.google.cloud.sql:mysql-socket-factory-connector-j-8" to "google.cloud.sql.mysql.socket.factory.connector.j8",
        "com.google.cloud.sql:postgres-socket-factory" to "google.cloud.sql.postgres.socket.factory",
        "com.google.cloud.sql:cloud-sql-connector-r2dbc-postgres" to "google.cloud.sql.connector.r2dbc.postgres",
        "com.google.cloud:cloud-spanner-r2dbc" to "google.cloud.spanner.r2dbc",
        "com.google.cloud:libraries-bom" to "google.cloud.libraries.bom",

        "io.fabric8:kubernetes-client-bom" to "fabric8.kubernetes.client.bom",
        "io.kubernetes:client-java" to "kubernetes.client.java",
        "io.kubernetes:client-java-extended" to "kubernetes.client.java.extended",
        "io.kubernetes:client-java-spring-integration" to "kubernetes.client.java.spring.integration",
    )

    val betterAliases = aliasedDependencies.map { aliasedDependency ->
        AliasedDependency(
            alias = replacements["${aliasedDependency.dependency.groupId}:${aliasedDependency.dependency.artifactId}"]
                ?: aliasedDependency.alias,
            dependency = aliasedDependency.dependency
        )
    }

    val betterAliasesWithBoms = springBootBoms.entries.map { (name, bom) ->
        AliasedDependency(
            alias = "${name.replace("-", ".")}.bom",
            dependency = Pom.DependencyManagement.Dependency(bom.groupId, bom.artifactId, bom.version)
        )
    }.filter { it.alias != "spring.grpc.bom" } + betterAliases

    val generatedCode = buildString {
        appendLine()
        betterAliasesWithBoms.forEach { aliasedDependency ->
            appendLine("            put(\"${aliasedDependency.alias}\", library(\"${aliasedDependency.dependency.groupId}:${aliasedDependency.dependency.artifactId}\", \"${aliasedDependency.dependency.version}\"))")
        }
        append("        ")
    }

    catalogKt.replaceFileText { text ->
        text.replaceRegexGroup1(
        """if \(springBootVersion != null\) \{(.*?)\}""".toRegex(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)),
            generatedCode
        )
    }
    
    println("Successfully updated catalog.kt with ${betterAliasesWithBoms.size} Spring Boot dependencies")
}


fun Path.replaceFileText(transform: (text: String) -> String) {
    val oldText = readText()
    val newTest = transform(oldText)
    if (oldText == newTest) {
        return
    }
    writeText(newTest)
    println("Updated file .${File.separator}${relativeTo(amperRootDir)}")
}

fun String.replaceRegexGroup1(regex: Regex, replacement: String) = replace(regex) {
    it.value.replace(it.groupValues[1], replacement)
}

main()

