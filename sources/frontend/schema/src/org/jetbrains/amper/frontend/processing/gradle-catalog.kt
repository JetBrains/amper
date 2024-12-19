/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.processing

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.util.childrenOfType
import org.jetbrains.amper.core.messages.ProblemReporterContext
import org.jetbrains.amper.frontend.FrontendPathResolver
import org.jetbrains.amper.frontend.VersionCatalog
import org.jetbrains.amper.frontend.api.PsiTrace
import org.jetbrains.amper.frontend.api.TraceableString
import org.toml.lang.psi.TomlFile
import org.toml.lang.psi.TomlInlineTable
import org.toml.lang.psi.TomlKey
import org.toml.lang.psi.TomlKeyValue
import org.toml.lang.psi.TomlKeyValueOwner
import org.toml.lang.psi.TomlLiteral
import org.toml.lang.psi.TomlTable

private val TomlTable.headerText: String?
    get() = header.key?.keyText

private val TomlKeyValue.keyText: String
    get() = key.keyText

private val TomlKey.keyText: String
    get() = segments.joinToString(".") { it.text }

private fun TomlKeyValueOwner.getStringValueOrNull(key: String): String? {
    val keyValue = entries.find { it.keyText == key } ?: return null
    return keyValue.value?.takeIf { it is TomlLiteral }?.text?.removeSurrounding("\"")
}

private fun TomlFile.findTableOrNull(headerText: String): TomlTable? =
    childrenOfType<TomlTable>().firstOrNull { it.headerText == headerText }

private data class TomlLibraryDefinition(
    val libraryString: String,
    val element: PsiElement,
)

private class TomlCatalog(
    private val libraries: Map<String, TomlLibraryDefinition>,
) : VersionCatalog {
    override val entries: Map<String, TraceableString>
        get() = libraries.map {
            val definition = it.value
            it.key to TraceableString(definition.libraryString)
                .apply { trace = PsiTrace(definition.element) }
        }.toMap()

    override val isPhysical: Boolean = true

    override fun findInCatalog(key: String) = entries[key]
}

/**
 * A gradle compliant version catalog, that supports only:
 * 1. `[versions]` and `[libraries]` sections, no `[plugins]` or `[bundles]`
 * 2. versions or version refs, no version constraints
 */
context(ProblemReporterContext)
fun FrontendPathResolver.parseGradleVersionCatalog(
    catalogFile: VirtualFile
): VersionCatalog? {
    val psiFile = toPsiFile(catalogFile) as? TomlFile ?: return null
    val libraries = psiFile.parseCatalogLibraries() ?: return null
    return TomlCatalog(libraries)
}

/**
 * Get `[libraries]` table, parse it and normalize libraries aliases
 * to match "libs.my.lib" format.
 */
context(ProblemReporterContext)
private fun TomlFile.parseCatalogLibraries(): Map<String, TomlLibraryDefinition>? {
    fun String.normalizeLibraryKey() = "libs." + replace("-", ".").replace("_", ".")

    val librariesTable = findTableOrNull("libraries") ?: return null
    val librariesAliases = librariesTable.entries
    return buildMap {
        librariesAliases.forEach { entry ->
            val aliasKey = entry.keyText.normalizeLibraryKey()

            // my-lib = "com.mycompany:mylib:1.4"
            val value = getInlineNotation(entry) ?: return@forEach
            put(aliasKey, TomlLibraryDefinition(value, entry))
        }
    }
}

private fun getInlineNotation(catalogEntry: TomlKeyValue): String? {
    return when (val libraryValue = catalogEntry.value) {
        is TomlLiteral -> libraryValue.text.removeSurrounding("\"")
        is TomlInlineTable -> {
            val version = libraryValue.getStringValueOrNull("version")
            val versionRef = libraryValue.getStringValueOrNull("version.ref")

            val module = libraryValue.getStringValueOrNull("module")
            val group = libraryValue.getStringValueOrNull("group")
            val name = libraryValue.getStringValueOrNull("name")

            val finalModuleName = when {
                module != null -> module
                group != null && name != null -> "$group:$name"
                else -> null
            } ?: return null

            // The version might come from BOM (currently supported only with Gradle)
            if (version == null && versionRef == null && module != null) return finalModuleName

            val finalVersion = when {
                version != null -> version
                versionRef != null -> {
                    val file = catalogEntry.containingFile as TomlFile
                    val versions = file.findTableOrNull("versions")
                    versions?.getStringValueOrNull(versionRef)
                }

                else -> null
            } ?: return null

            "$finalModuleName:$finalVersion"
        }

        else -> null
    }
}
