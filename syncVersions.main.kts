/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.FileVisitResult
import java.nio.file.Path
import kotlin.io.path.*

/*
This script is meant to be used to update several user-facing versions in:
- the Amper project itself
- our examples
- our docs

The source of truth is the list of versions at the top of this file.
 */

val bootstrapAmperVersion = "0.8.0-dev-2886" // AUTO-UPDATED BY THE CI - DO NOT RENAME

/**
 * This is the version of the JetBrains Runtime that Amper wrappers use to run the Amper dist.
 *
 * See glibc compatibility: https://youtrack.jetbrains.com/issue/JBR-7511/Centos-7-support-is-over
 * We need the JBR for 2024.2 to be compatible with glibc 2.17.
 * Check https://github.com/JetBrains/JetBrainsRuntime?tab=readme-ov-file#releases-based-on-jdk-21
 */
val amperInternalJbrVersion = "21.0.6-b895.97"

/**
 * The Kotlin version used in both standalone and Gradle-based Amper.
 * Bumping this can force the AGP to be bumped too.
 * See the [compatiblity table](https://developer.android.com/build/kotlin-support).
 */
val kotlinVersion = "2.1.20"

val composeVersion = "1.8.0"
val composeVersionForGradleBasedAmper = "1.6.10"
val hotReloadVersion = "1.0.0-alpha02"
val junitPlatformVersion = "1.12.1"
val kotlinxSerializationVersion = "1.8.0"
val kspVersion = "2.1.21-2.0.1" // KSP2 still has some Kotlin version in it, but it doesn't have to be in sync
val ktorVersion = "3.1.1"
val logbackVersion = "1.5.18"
val slf4jVersion = "2.0.17"
val springAiTimeFoldVersion = "1.20.1"
val springAiVersion = "1.0.0-M6"
val springBootVersion = "3.4.3"
val springCloudAzureVersion = "5.21.0"
val springCloudGcpVersion = "6.1.1"
val springCloudServicesVersion = "4.2.0"
val springCloudVersion = "2024.0.1"
val springShellVersion = "3.4.0"
val lombokVersion = "1.18.38"

/**
 * This is the Gradle version used in Gradle-based Amper projects, not Amper itself.
 */
val gradleVersion = "8.11.1"

/**
 * This is the version of AGP used in Gradle-based Amper (not the one for standalone, which is internal).
 * It may be limited by the current default [gradleVersion] we use (see above).
 * See the [compatibility table](https://developer.android.com/build/releases/gradle-plugin#updating-gradle).
 */
// 8.3.0 fails with "Provided Metadata instance has version 2.1.0, while maximum supported version is 2.0.0"
// 8.4.0 fails:
//   GradleIntegrationTest.multiplatform (AndroidWorldTest.doTest fails with NoClassDefFoundError)
//   GradleIntegrationTest.testing android common resources propagation
// 8.6.0 fails with the same errors as 8.4 and 8.3
// 8.7.x fails with missing build.gradle input file in LintModelWriterTask
val androidVersionForGradleBasedAmper = "8.2.0"

val amperMavenRepoUrl = "https://packages.jetbrains.team/maven/p/amper/amper"

val amperRootDir: Path = __FILE__.toPath().absolute().parent // __FILE__ is this script
val examplesGradleDir = amperRootDir / "examples-gradle"
val amperWrapperModuleDir = amperRootDir / "sources/amper-wrapper"
val docsDir = amperRootDir / "docs"
val versionsCatalogToml = amperRootDir / "gradle/libs.versions.toml"
val usedVersionsKt = amperRootDir / "sources/core/src/org/jetbrains/amper/core/UsedVersions.kt"

fun syncVersions() {
    println("Making sure user-visible versions are aligned in Amper, docs, and examples...")
    updateVersionsCatalog()
    updateUsedVersionsKt()
    updateDocs()
    updateAmperWrappers()
    updateGradleFiles()
    updateWrapperTemplates()
    println("Done.")
}

fun updateVersionsCatalog() {
    versionsCatalogToml.replaceFileText { text ->
        text
            .replaceCatalogVersionVariable(variableName = "kotlin", newValue = kotlinVersion)
            .replaceCatalogVersionVariable(variableName = "hot-reload-version", newValue = hotReloadVersion)
            .replaceCatalogVersionVariable(variableName = "junit-platform", newValue = junitPlatformVersion)
    }
}

fun String.replaceCatalogVersionVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
    regex = Regex("""^${Regex.escape(variableName)}\s*=\s*\"([^"]+)\"""", RegexOption.MULTILINE),
    replacement = newValue,
)

fun updateUsedVersionsKt() {
    usedVersionsKt.replaceFileText { text ->
        text
            .replaceBundledVersionVariable(variableName = "kotlinVersion", newValue = kotlinVersion)
            .replaceBundledVersionVariable(variableName = "kotlinxSerializationVersion", newValue = kotlinxSerializationVersion)
            .replaceBundledVersionVariable(variableName = "composeVersion", newValue = composeVersion)
            .replaceBundledVersionVariable(variableName = "junitPlatform", newValue = junitPlatformVersion)
            .replaceBundledVersionVariable(variableName = "kspVersion", newValue = kspVersion)
            .replaceBundledVersionVariable(variableName = "hotReloadVersion", newValue = hotReloadVersion)
            .replaceBundledVersionVariable(variableName = "springBootVersion", newValue = springBootVersion)
            .replaceBundledVersionVariable(variableName = "springCloudVersion", newValue = springCloudVersion)
            .replaceBundledVersionVariable(variableName = "springCloudAzureVersion", newValue = springCloudAzureVersion)
            .replaceBundledVersionVariable(variableName = "springCloudGcpVersion", newValue = springCloudGcpVersion)
            .replaceBundledVersionVariable(variableName = "springCloudServicesVersion", newValue = springCloudServicesVersion)
            .replaceBundledVersionVariable(variableName = "springShellVersion", newValue = springShellVersion)
            .replaceBundledVersionVariable(variableName = "springAiVersion", newValue = springAiVersion)
            .replaceBundledVersionVariable(variableName = "springAiTimeFoldVersion", newValue = springAiTimeFoldVersion)
            .replaceBundledVersionVariable(variableName = "slf4jVersion", newValue = slf4jVersion)
            .replaceBundledVersionVariable(variableName = "ktorVersion", newValue = ktorVersion)
            .replaceBundledVersionVariable(variableName = "logbackVersion", newValue = logbackVersion)
            .replaceBundledVersionVariable(variableName = "lombokVersion", newValue = lombokVersion)
    }
}

fun String.replaceBundledVersionVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
    regex = Regex("""\/\*magic_replacement\*\/\s*val\s+${Regex.escape(variableName)}\s*=\s*\"([^"]+)\""""),
    replacement = newValue,
)

fun updateDocs() {
    docsDir.walk().filter { it.name.endsWith(".md") }.replaceEachFileText { fileText ->
        fileText
            // For wrapper dist download URLs in Usage.md
            .replace(Regex("""/amper-cli/([^/]+)/amper-cli-\1-wrapper"""),"/amper-cli/$bootstrapAmperVersion/amper-cli-$bootstrapAmperVersion-wrapper")
            // For Gradle-based Amper setup instructions
            .replaceAmperGradlePluginVersion()
            // For Documentation.md and GradleMigration.md files.
            // These are in tables listing the Gradle plugins for which we can't change the version in Gradle-based Amper
            .replaceInMarkdownTable(pluginId = "org.jetbrains.kotlin.multiplatform", version = kotlinVersion)
            .replaceInMarkdownTable(pluginId = "org.jetbrains.kotlin.plugin.serialization", version = kotlinVersion)
            .replaceInMarkdownTable(pluginId = "org.jetbrains.kotlin.android", version = kotlinVersion)
            .replaceInMarkdownTable(pluginId = "org.jetbrains.compose", version = composeVersionForGradleBasedAmper)
            .replaceInMarkdownTable(pluginId = "com.android.library", version = androidVersionForGradleBasedAmper)
            .replaceInMarkdownTable(pluginId = "com.android.application", version = androidVersionForGradleBasedAmper)
    }
}

// For Documentation.md and GradleMigration.md files
fun String.replaceInMarkdownTable(pluginId: String, version: String): String =
    replaceRegexGroup1(Regex("""`${Regex.escape(pluginId)}`\s*\|\s*([^|\s]+)\s*\|"""), version)

fun updateAmperWrappers() {
    val shellWrapperText = fetchContent("$amperMavenRepoUrl/org/jetbrains/amper/amper-cli/$bootstrapAmperVersion/amper-cli-$bootstrapAmperVersion-wrapper")
    val batchWrapperText = fetchContent("$amperMavenRepoUrl/org/jetbrains/amper/amper-cli/$bootstrapAmperVersion/amper-cli-$bootstrapAmperVersion-wrapper.bat")

    amperRootDir.forEachWrapperFile { path ->
        when (path.name) {
            "amper" -> path.replaceFileText { shellWrapperText }
            "amper.bat" -> path.replaceFileText { batchWrapperText }
        }
    }
}

private val excludedDirs = setOf("build", "build-from-sources", ".gradle", ".kotlin", ".git", "shared test caches")

fun Path.forEachWrapperFile(action: (Path) -> Unit) {
    visitFileTree {
        onPreVisitDirectory { dir, _ ->
            if (dir.name in excludedDirs) {
                FileVisitResult.SKIP_SUBTREE
            } else {
                FileVisitResult.CONTINUE
            }
        }
        onVisitFile { file, _ ->
            if (file.name in setOf("amper", "amper.bat")) {
                action(file)
            }
            FileVisitResult.CONTINUE
        }
    }
}

fun updateWrapperTemplates() {
    val jbrVersionRegex = Regex("""(?<version>\d+\.\d+\.\d+)-?(?<build>b.*)""")
    val match = jbrVersionRegex.matchEntire(amperInternalJbrVersion) ?: error("Invalid JBR version '$jbrVersionRegex'")
    val jvmVersion = match.groups["version"]!!.value
    val jbrBuild = match.groups["build"]!!.value

    val jbrs = getJbrChecksums(jvmVersion, jbrBuild)

    sequenceOf(
        amperWrapperModuleDir / "resources/wrappers/amper.template.sh",
        amperRootDir / "amper-from-sources",
    ).replaceEachFileText { initialText ->
        val textWithVersion = initialText
            .replaceRegexGroup1(Regex("""\bjbr_version=(\S+)"""), jvmVersion)
            .replaceRegexGroup1(Regex("""\bjbr_build=(\S+)"""), jbrBuild)
        jbrs.fold(textWithVersion) { text, (os, arch, checksum) ->
            text.replaceRegexGroup1(Regex(""""$os $arch"\)\s+jbr_sha512=(\S+)\s*;;"""), checksum)
        }
    }

    sequenceOf(
        amperWrapperModuleDir / "resources/wrappers/amper.template.bat",
        amperRootDir / "amper-from-sources.bat",
    ).replaceEachFileText { initialText ->
        val textWithVersion = initialText
            .replaceRegexGroup1(Regex("""\bset\s+jbr_version=(\S+)"""), jvmVersion)
            .replaceRegexGroup1(Regex("""\bset\s+jbr_build=(\S+)"""), jbrBuild)
        jbrs.filter { it.os == "windows" }.fold(textWithVersion) { text, (_, arch, checksum) ->
            text.replaceRegexGroup1(Regex("""set jbr_arch=$arch\s+set jbr_sha512=(\S+)""", RegexOption.MULTILINE), checksum)
        }
    }
}

data class Jbr(val os: String, val arch: String, val sha512: String)

fun getJbrChecksums(jvmVersion: String, jbrBuild: String): List<Jbr> = listOf("windows", "linux", "osx").flatMap { os ->
    listOf("aarch64", "x64").map { arch ->
        Jbr(
            os = os,
            arch = arch,
            sha512 = fetchContent("https://cache-redirector.jetbrains.com/intellij-jbr/jbr-$jvmVersion-$os-$arch-$jbrBuild.tar.gz.checksum")
                .trim()
                .split(" ")
                .first()
        )
    }
}

fun updateGradleFiles() {
    examplesGradleDir.walk().forEach { path ->
        when (path.name) {
            "settings.gradle.kts" -> path.replaceFileText { it.replaceAmperGradlePluginVersion() }
            "gradle-wrapper.properties" -> path.replaceFileText { it.replaceGradleDistributionUrl() }
        }
    }
}

fun String.replaceAmperGradlePluginVersion() = replaceRegexGroup1(
    regex = Regex("""id\("org\.jetbrains\.amper\.settings\.plugin"\)\.version\(\"([^"]+)"\)"""),
    replacement = bootstrapAmperVersion
)

fun String.replaceGradleDistributionUrl() = replaceRegexGroup1(
    // there is a backslash to escape the colon in .properties files: 'https\://services.gradle.org/...'
    regex = Regex("""https\\://services\.gradle\.org/distributions/gradle-(.+)-bin.zip"""),
    replacement = gradleVersion,
)

fun fetchContent(url: String): String {
    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
    val request = HttpRequest.newBuilder().uri(URI.create(url)).build()
    return client.send(request, HttpResponse.BodyHandlers.ofString()).body()
}

/**
 * Finds all matches for the given [regex] in this string, and replaces the matched group 1 with the given replacement.
 */
fun String.replaceRegexGroup1(regex: Regex, replacement: String) = replace(regex) {
    it.value.replace(it.groupValues[1], replacement)
}

/**
 * Replaces the contents of each file in this sequence using the given [transform] on the existing contents.
 */
fun Sequence<Path>.replaceEachFileText(transform: (text: String) -> String) = forEach { it.replaceFileText(transform) }

/**
 * Replaces the contents of the file at this [Path] using the given [transform] on the existing contents.
 */
fun Path.replaceFileText(transform: (text: String) -> String) {
    val oldText = readText()
    val newTest = transform(oldText)
    if (oldText == newTest) {
        return
    }
    writeText(newTest)
    println("Updated file .${File.separator}${relativeTo(amperRootDir)}")
}

// actually runs the script
syncVersions()
