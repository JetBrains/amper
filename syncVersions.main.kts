/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

@file:OptIn(ExperimentalPathApi::class)

import java.net.URI
import java.nio.file.Path
import kotlin.io.path.*

/*
This script is meant to be used to update several user-facing versions in:
- the Amper project itself
- our examples
- our docs

The source of truth is the list of versions at the top of this file.
 */

val bootstrapAmperVersion = "0.5.0-dev-1696" // AUTO-UPDATED BY THE CI - DO NOT RENAME

val kotlinVersion = "2.0.20"
val kotlinxSerializationVersion = "1.7.3"
val kspVersion = "$kotlinVersion-1.0.25"
val composeVersion = "1.6.10"

/** This is the Gradle version used in Gradle-based Amper projects, not Amper itself. */
val gradleVersion = "8.6"

/** This is the version of AGP used in Gradle-based Amper (not the one for standalone, which is internal). */
val androidVersionForGradleBasedAmper = "8.2.0" // do not bump higher than Fleet can import

val amperMavenRepoUrl = "https://packages.jetbrains.team/maven/p/amper/amper"

val amperRootDir: Path = __FILE__.toPath().absolute().parent // __FILE__ is this script
val examplesStandaloneDir = amperRootDir / "examples-standalone"
val examplesGradleDir = amperRootDir / "examples-gradle"
val migratedProjectsDir = amperRootDir / "migrated-projects"
val testDataProjectsDir = amperRootDir / "sources/amper-backend-test/testData"
val docsDir = amperRootDir / "docs"
val versionsCatalogToml = amperRootDir / "gradle/libs.versions.toml"
val usedVersionsKt = amperRootDir / "sources/core/src/org/jetbrains/amper/core/UsedVersions.kt"

// actually runs the script
syncVersions()

fun syncVersions() {
    println("Making sure user-visible versions are aligned in Amper, docs, and examples...")
    updateVersionsCatalog()
    updateUsedVersionsKt()
    updateDocs()
    updateAmperWrappers()
    updateGradleFiles()
}

fun updateVersionsCatalog() {
    versionsCatalogToml.replaceFileText { text ->
        text
            .replaceCatalogVersionVariable(variableName = "android-forGradleBased", newValue = androidVersionForGradleBasedAmper)
            .replaceCatalogVersionVariable(variableName = "kotlin", newValue = kotlinVersion)
            .replaceCatalogVersionVariable(variableName = "compose", newValue = composeVersion)
            .replaceCatalogVersionVariable(variableName = "gradle-api-forGradleBased", newValue = gradleVersion)
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
            .replaceBundledVersionVariable(variableName = "kspVersion", newValue = kspVersion)
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
            .replace(Regex("""/cli/([^/]+)/cli-\1-wrapper"""),"/cli/$bootstrapAmperVersion/cli-$bootstrapAmperVersion-wrapper")
            // For Gradle-based Amper setup instructions
            .replaceAmperGradlePluginVersion()
            // For Documentation.md and GradleMigration.md files.
            // These are in tables listing the Gradle plugins for which we can't change the version in Gradle-based Amper
            .replaceInMarkdownTable(pluginId = "org.jetbrains.kotlin.multiplatform", version = kotlinVersion)
            .replaceInMarkdownTable(pluginId = "org.jetbrains.kotlin.plugin.serialization", version = kotlinVersion)
            .replaceInMarkdownTable(pluginId = "org.jetbrains.kotlin.android", version = kotlinVersion)
            .replaceInMarkdownTable(pluginId = "org.jetbrains.compose", version = composeVersion)
            .replaceInMarkdownTable(pluginId = "com.android.library", version = androidVersionForGradleBasedAmper)
            .replaceInMarkdownTable(pluginId = "com.android.application", version = androidVersionForGradleBasedAmper)
    }
}

// For Documentation.md and GradleMigration.md files
fun String.replaceInMarkdownTable(pluginId: String, version: String): String =
    replaceRegexGroup1(Regex("""`${Regex.escape(pluginId)}`\s*\|\s*([^|\s]+)\s*\|"""), version)

fun updateAmperWrappers() {
    val shellWrapperText = fetchContent("$amperMavenRepoUrl/org/jetbrains/amper/cli/$bootstrapAmperVersion/cli-$bootstrapAmperVersion-wrapper")
    val batchWrapperText = fetchContent("$amperMavenRepoUrl/org/jetbrains/amper/cli/$bootstrapAmperVersion/cli-$bootstrapAmperVersion-wrapper.bat")

    (amperRootDir / "amper").replaceFileText { shellWrapperText }
    (amperRootDir / "amper.bat").replaceFileText { batchWrapperText }

    (examplesStandaloneDir.walk() + testDataProjectsDir.walk()).forEach { path ->
        when (path.name) {
            "amper" -> path.replaceFileText { shellWrapperText }
            "amper.bat" -> path.replaceFileText { batchWrapperText }
        }
    }
}

fun fetchContent(url: String) = URI(url).toURL().readText()

fun updateGradleFiles() {
    (amperRootDir / "settings.gradle.kts").replaceFileText { it.replaceAmperGradlePluginVersion() }

    (examplesGradleDir.walk() + migratedProjectsDir.walk()).forEach { path ->
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
    println("Updated file ./${relativeTo(amperRootDir)}")
}
