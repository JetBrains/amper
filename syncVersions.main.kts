/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

val bootstrapAmperVersion = "0.10.0-dev-3657" // AUTO-UPDATED BY THE CI - DO NOT RENAME

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
val kotlinVersion = "2.2.21" // /!\ Remember to update the KotlinVersion enum with outdated/experimental versions

val composeHotReloadVersion = "1.0.0-rc01"
val composeVersion = "1.10.1"
val jdkVersion = "21" // TODO bump to 25 when Kotlin supports it
val junitPlatformVersion = "6.0.1"
val kotlinxRpcVersion = "0.10.1"
val kotlinxSerializationVersion = "1.9.0"
val kspVersion = "2.3.0"
val ktorVersion = "3.2.3"
val springBootVersion = "4.0.2"

val amperMavenRepoUrl = "https://packages.jetbrains.team/maven/p/amper/amper"

val amperRootDir: Path = __FILE__.toPath().absolute().parent // __FILE__ is this script
val amperWrapperModuleDir = amperRootDir / "sources/amper-wrapper"
val docsDir = amperRootDir / "docs"
val versionsCatalogToml = amperRootDir / "libs.versions.toml"
val defaultVersionsKt = amperRootDir / "sources/frontend-api/src/org/jetbrains/amper/frontend/schema/DefaultVersions.kt"

fun syncVersions() {
    println("Making sure user-visible versions are aligned in Amper, docs, and examples...")
    updateVersionsCatalog()
    updateDefaultVersionsKt()
    updateAmperWrappers()
    updateWrapperTemplates()
    println("Done.")
}

fun updateVersionsCatalog() {
    versionsCatalogToml.replaceFileText { text ->
        text
            .replaceCatalogVersionVariable(variableName = "kotlin", newValue = kotlinVersion)
            .replaceCatalogVersionVariable(variableName = "ksp", newValue = kspVersion)
            .replaceCatalogVersionVariable(variableName = "compose-hot-reload-version", newValue = composeHotReloadVersion)
            .replaceCatalogVersionVariable(variableName = "junit-platform", newValue = junitPlatformVersion)
    }
}

fun String.replaceCatalogVersionVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
    regex = Regex("""^${Regex.escape(variableName)}\s*=\s*\"([^"]+)\"""", RegexOption.MULTILINE),
    replacement = newValue,
)

fun updateDefaultVersionsKt() {
    defaultVersionsKt.replaceFileText { text ->
        text
            .replaceDefaultVersionVariable(variableName = "compose", newValue = composeVersion)
            .replaceDefaultVersionVariable(variableName = "composeHotReload", newValue = composeHotReloadVersion)
            .replaceDefaultVersionIntVariable(variableName = "jdk", newValue = jdkVersion)
            .replaceDefaultVersionVariable(variableName = "junitPlatform", newValue = junitPlatformVersion)
            .replaceDefaultVersionVariable(variableName = "kotlin", newValue = kotlinVersion)
            .replaceDefaultVersionVariable(variableName = "kotlinxRpc", newValue = kotlinxRpcVersion)
            .replaceDefaultVersionVariable(variableName = "kotlinxSerialization", newValue = kotlinxSerializationVersion)
            .replaceDefaultVersionVariable(variableName = "ksp", newValue = kspVersion)
            .replaceDefaultVersionVariable(variableName = "ktor", newValue = ktorVersion)
            .replaceDefaultVersionVariable(variableName = "springBoot", newValue = springBootVersion)
    }
}

fun String.replaceDefaultVersionVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
    regex = Regex("""\/\*managed_default\*\/\s*val\s+${Regex.escape(variableName)}\s*=\s*\"([^"]+)\""""),
    replacement = newValue,
)

fun String.replaceDefaultVersionIntVariable(variableName: String, newValue: String): String = replaceRegexGroup1(
    regex = Regex("""\/\*managed_default\*\/\s*val\s+${Regex.escape(variableName)}\s*=\s*(\d+)"""),
    replacement = newValue,
)

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
