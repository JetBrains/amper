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

fun syncSpringBootDependencies() {
    val springBootVersion = getSpringBootVersionFromSyncVersions()
    println("Using Spring Boot version $springBootVersion")

    val springBootBom = SpringBootDependencies.Bom(
        "org.springframework.boot",
        "spring-boot-dependencies",
        springBootVersion
    )
    val dependencies = dependencies(springBootBom)

    val filteredDependencies = dependencies.filter { it.groupId == "org.springframework.boot" }
    val deduplicatedDependencies = filteredDependencies.distinctBy { "${it.groupId}:${it.artifactId}" }

    val aliasedDependencies = deduplicatedDependencies.map { dependency ->
        AliasedDependency(
            alias = dependency.artifactId
                .replace("-", ".")
                .replace("_", "."),
            dependency = dependency
        )
    }


    val generatedCode = buildString {
        appendLine()
        aliasedDependencies.forEach { aliasedDependency ->
            appendLine("            put(\"${aliasedDependency.alias}\", library(\"${aliasedDependency.dependency.groupId}:${aliasedDependency.dependency.artifactId}\", springBootVersion))")
        }
        append("        ")
    }

    catalogKt.replaceFileText { text ->
        text.replaceRegexGroup1(
        """if \(springBootVersion != null\) \{(.*?)\}""".toRegex(setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)),
            generatedCode
        )
    }
    
    println("Successfully updated catalog.kt with ${aliasedDependencies.size} Spring Boot dependencies")
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

syncSpringBootDependencies()

