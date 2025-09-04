/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

// Disabled because it breaks Amper update builds in Amper
// (the springBootDependenciesSync.main.kts script doesn't compile with TeamCity's Kotlin version)
//@file:Import("springBootDependenciesSync.main.kts")

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

val bootstrapAmperVersion = "0.8.0-dev-3161" // AUTO-UPDATED BY THE CI - DO NOT RENAME

/**
 * This is the version of the JetBrains Runtime that Amper wrappers use to run the Amper dist.
 *
 * See glibc compatibility: https://youtrack.jetbrains.com/issue/JBR-7511/Centos-7-support-is-over
 * We need the JBR for 2024.2 to be compatible with glibc 2.17.
 * Check https://github.com/JetBrains/JetBrainsRuntime?tab=readme-ov-file#releases-based-on-jdk-21
 */
val amperInternalJbrVersion = "21.0.8b1038.68"

/**
 * The Kotlin version used in Amper projects (not customizable yet).
 * Make sure we respect the constraints of the Android Gradle plugin (used internally in delegated Gradle builds).
 * See the [compatiblity table](https://developer.android.com/build/kotlin-support).
 */
val kotlinVersion = "2.2.0" // /!\ Remember to update the KotlinVersion enum with outdated/experimental versions

val composeVersion = "1.8.2"
val hotReloadVersion = "1.0.0-beta03"
val junitPlatformVersion = "1.12.1"
val kotlinxSerializationVersion = "1.9.0"
val kspVersion = "2.2.0-2.0.2" // KSP2 still has some Kotlin version in it, but it doesn't have to be in sync
val ktorVersion = "3.2.3"
val springBootVersion = "3.5.5"

val amperMavenRepoUrl = "https://packages.jetbrains.team/maven/p/amper/amper"

val amperRootDir: Path = __FILE__.toPath().absolute().parent // __FILE__ is this script
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
    updateWrapperTemplates()
    println("Done.")
}

fun updateVersionsCatalog() {
    versionsCatalogToml.replaceFileText { text ->
        text
            .replaceCatalogVersionVariable(variableName = "kotlin", newValue = kotlinVersion)
            .replaceCatalogVersionVariable(variableName = "ksp", newValue = kspVersion)
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
            .replaceBundledVersionVariable(variableName = "defaultKotlinVersion", newValue = kotlinVersion)
            .replaceBundledVersionVariable(variableName = "kotlinxSerializationVersion", newValue = kotlinxSerializationVersion)
            .replaceBundledVersionVariable(variableName = "composeVersion", newValue = composeVersion)
            .replaceBundledVersionVariable(variableName = "junitPlatform", newValue = junitPlatformVersion)
            .replaceBundledVersionVariable(variableName = "kspVersion", newValue = kspVersion)
            .replaceBundledVersionVariable(variableName = "hotReloadVersion", newValue = hotReloadVersion)
            .replaceBundledVersionVariable(variableName = "ktorVersion", newValue = ktorVersion)
            .replaceBundledVersionVariable(variableName = "springBootVersion", newValue = springBootVersion)
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
    }
}

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

// Disabled because it breaks Amper update builds in Amper
// (the springBootDependenciesSync.main.kts script doesn't compile with TeamCity's Kotlin version)
// run spring boot dependencies
//syncSpringBootDependencies()
