/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.tomlj.Toml
import org.tomlj.TomlInvalidTypeException
import org.tomlj.TomlParseResult
import org.tomlj.TomlTable
import java.nio.file.Path


/**
 * A gradle compliant version catalog, that supports only:
 * 1. `[versions]` and `[libraries]` sections, no `[plugins]` or `[bundles]`
 * 2. versions or version refs, no version constraints
 */
context(ProblemReporterContext)
fun parseGradleVersionCatalog(
    catalogPath: Path
): PredefinedCatalog? {
    val parsed = Toml.parse(catalogPath)
//    parsed.errors().forEach {
        // TODO Report parsing errors.
//    }
    val versions = parsed.parseCatalogVersions()
    val libraries = parsed.parseCatalogLibraries(versions) ?: return null
    return PredefinedCatalog(libraries)
}

context(ProblemReporterContext)
fun TomlParseResult.parseCatalogVersions(): Map<String, String> {
    val versionsTable = getTableOrNull("versions") ?: return emptyMap()
    val versionKeys = versionsTable.keySet() ?: return emptyMap()
    return versionKeys
        .mapNotNull { key -> versionsTable.getStringOrNull(key)?.let { key to it } }
        .toMap()
}

/**
 * Get `[libraries]` table, parse it and normalize libraries aliases
 * to match "libs.my.lib" format.
 */
context(ProblemReporterContext)
fun TomlParseResult.parseCatalogLibraries(
    versions: Map<String, String>,
): Map<String, String>? {
    fun String.normalizeLibraryKey() = "libs." + replace("-", ".")

    val librariesTable = getTableOrNull("libraries") ?: return null
    val librariesAliases = librariesTable.keySet() ?: return null
    return buildMap {
        librariesAliases.forEach { alias ->
            val aliasKey = alias.normalizeLibraryKey()

            // my-lib = "com.mycompany:mylib:1.4"
            val libraryString = librariesTable.getStringOrNull(alias)
            if (libraryString != null) put(aliasKey, libraryString)

            // my-lib = { module = "com.mycompany:mylib", version = "1.4" }
            val libraryTable = librariesTable.getTableOrNull(alias)
            if (libraryTable != null) {
                val module = libraryTable.getStringOrNull("module")
                val group = libraryTable.getStringOrNull("group")
                val name = libraryTable.getStringOrNull("name")
                val version = libraryTable.getStringOrNull("version")
                val versionRef = libraryTable.getStringOrNull("version.ref")

                val finalVersion = version ?: versionRef?.let { versions[it] }
                val finalLibraryModule = when {
                    module != null -> module
                    group != null && name != null -> "$group:$name"
                    else -> null
                }
                // Just skip libraries without module or version.
                if (finalLibraryModule != null && finalVersion != null) {
                    put(aliasKey, "$finalLibraryModule:$finalVersion")
                }
            }
        }
    }
}

fun TomlTable.getStringOrNull(dottedPath: String): String? =
    try { getString(dottedPath) } catch (ex: TomlInvalidTypeException) { null }

fun TomlTable.getTableOrNull(dottedPath: String): TomlTable? =
    try { getTable(dottedPath) } catch (ex: TomlInvalidTypeException) { null }