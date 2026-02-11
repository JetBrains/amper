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

val bootstrapAmperVersion = "0.10.0-dev-3670" // AUTO-UPDATED BY THE CI - DO NOT RENAME

/**
 * This is the version of the Zulu JRE that Amper wrappers use to run the Amper dist.
 *
 * To find the latest version, check:
 * https://api.azul.com/metadata/v1/zulu/packages/?java_version=25&java_package_type=jre&latest=true&release_status=ga&page=1&page_size=1
 */
val amperJreZuluVersion = "25.32.21"
val amperJreJavaVersion = "25.0.2"

/**
 * The Kotlin version used in Amper projects (not customizable yet).
 * Make sure we respect the constraints of the Android Gradle plugin (used internally in delegated Gradle builds).
 * See the [compatiblity table](https://developer.android.com/build/kotlin-support).
 */
val kotlinVersion = "2.3.10" // /!\ Remember to update the KotlinVersion enum with outdated/experimental versions

val composeHotReloadVersion = "1.0.0-rc01"
val composeVersion = "1.10.1"
val jdkVersion = "21" // TODO bump to 25 when Kotlin supports it
val junitPlatformVersion = "6.0.1"
val kotlinxRpcVersion = "0.10.2"
val kotlinxSerializationVersion = "1.10.0"
val kspVersion = "2.3.6"
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
    val zuluVersion = amperJreZuluVersion
    val javaVersion = amperJreJavaVersion

    val jres = getJreChecksums(zuluVersion, javaVersion)

    sequenceOf(
        amperWrapperModuleDir / "resources/wrappers/amper.template.sh",
        amperRootDir / "amper-from-sources",
    ).replaceEachFileText { initialText ->
        val textWithVersion = initialText
            .replaceRegexGroup1(Regex("""^\s*zulu_version=(\S+)""", RegexOption.MULTILINE), zuluVersion)
            .replaceRegexGroup1(Regex("""^\s*java_version=(\S+)""", RegexOption.MULTILINE), javaVersion)
        jres.fold(textWithVersion) { text, (os, arch, checksum) ->
            text.replaceRegexGroup1(Regex(""""${os.filenameValue} ${arch.filenameValue}"\)\s+jre_sha256=(\w+)"""), checksum)
        }
    }

    sequenceOf(
        amperWrapperModuleDir / "resources/wrappers/amper.template.bat",
        amperRootDir / "amper-from-sources.bat",
    ).replaceEachFileText { initialText ->
        val textWithVersion = initialText
            .replaceRegexGroup1(Regex("""^\s*set\s+zulu_version=(\S+)""", RegexOption.MULTILINE), zuluVersion)
            .replaceRegexGroup1(Regex("""^\s*set\s+java_version=(\S+)""", RegexOption.MULTILINE), javaVersion)
        jres.filter { it.os == Os.Windows }.fold(textWithVersion) { text, (_, arch, checksum) ->
            text.replaceRegexGroup1(Regex("""set jre_arch=${arch.filenameValue}\s+set jre_sha256=(\S+)""", RegexOption.MULTILINE), checksum)
        }
    }
}

// archive types should match what we do in Amper wrappers
enum class Os(val azulApiName: String, val filenameValue: String, val archiveType: String) {
    Windows(azulApiName = "windows", filenameValue = "win", archiveType = "zip"),
    Linux(azulApiName = "linux-glibc", filenameValue = "linux", archiveType = "tar.gz"),
    MacOs(azulApiName = "osx", filenameValue = "macosx", archiveType = "tar.gz"),
}

enum class Arch(val azulApiName: String, val filenameValue: String) {
    Aarch64(azulApiName = "aarch64", filenameValue = "aarch64"),
    X64(azulApiName = "x64", filenameValue = "x64"),
}

data class Jre(val os: Os, val arch: Arch, val sha256: String)

fun getJreChecksums(zuluVersion: String, javaVersion: String): List<Jre> = Os.entries.flatMap { os ->
    Arch.entries.map { arch ->
        Jre(
            os = os,
            arch = arch,
            sha256 = fetchJreChecksum(zuluVersion, javaVersion, os, arch),
        )
    }
}

fun fetchJreChecksum(zuluVersion: String, javaVersion: String, os: Os, arch: Arch): String {
    val uuid = fetchJreUuid(zuluVersion, javaVersion, os, arch)
    val packageMetadataUrl = "https://api.azul.com/metadata/v1/zulu/packages/$uuid"
    val downloadUrl = fetchJsonField(url = packageMetadataUrl, fieldName = "download_url")
    val expectedDownloadUrl = downloadUrlFor(zuluVersion, javaVersion, os, arch)
    check(downloadUrl == expectedDownloadUrl) {
        "Incorrect package found, download URL is not as expected:\nExpected: $expectedDownloadUrl\nBut was:  $downloadUrl"
    }
    return fetchJsonField(
        url = packageMetadataUrl,
        fieldName = "sha256_hash",
    )
}

fun downloadUrlFor(zuluVersion: String, javaVersion: String, os: Os, arch: Arch): String =
    "https://cdn.azul.com/zulu/bin/zulu$zuluVersion-ca-jre$javaVersion-${os.filenameValue}_${arch.filenameValue}.${os.archiveType}"

fun fetchJreUuid(zuluVersion: String, javaVersion: String, os: Os, arch: Arch): String = fetchJsonField(
    url = "https://api.azul.com/metadata/v1/zulu/packages/?" +
            "java_version=$javaVersion" +
            "&distro_version=$zuluVersion" +
            "&os=${os.azulApiName}" +
            "&arch=${arch.azulApiName}" +
            "&archive_type=${os.archiveType}" +
            "&java_package_type=jre" +
            "&javafx_bundled=false" +
            "&crac_supported=false" +
            "&latest=true" +
            "&release_status=ga" +
            "&page=1" +
            "&page_size=1",
    fieldName = "package_uuid",
)

fun fetchJsonField(url: String, fieldName: String): String {
    val json = fetchContent(url)
    val matches = Regex(""""$fieldName"\s*:\s*"(?<value>[^"]+)"""").findAll(json).toList()
    check(matches.isNotEmpty()) { "Could not find $fieldName in the contents of $url:\n$json" }
    check(matches.size == 1) { "$fieldName was found multiple times in the contents of $url:\n$json" }
    val match = matches.single()
    return match.groups["value"]?.value
        ?: error("$fieldName was matched by the regex but cannot be extracted. Groups: ${match.groups}")
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
