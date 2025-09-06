/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.amper.core.UsedInIdePlugin
import org.jetbrains.amper.frontend.api.TraceableString

/**
 * Version catalog. Currently, it supports only maven dependencies.
 */
sealed interface VersionCatalog {

    /**
     * Get all declared catalog entry keys.
     */
    val entries: Map<String, TraceableString>

    /**
     * Whether any of the catalog entries have representation on the disk.
     */
    @Deprecated(
        message = "This doesn't play well with composite catalogs. Check the subtypes of the VersionCatalog instead.",
        replaceWith = ReplaceWith(
            expression = "this is FileVersionCatalog",
            imports = ["org.jetbrains.amper.frontend.FileVersionCatalog"],
        )
    )
    val isPhysical: Boolean

    /**
     * Get dependency notation by key.
     */
    fun findInCatalog(key: String) = entries[key]
}

/**
 * A [VersionCatalog] that is defined in memory and isn't backed by a physical file (for instance built-in catalogs).
 */
interface InMemoryVersionCatalog : VersionCatalog {

    @Deprecated(
        message = "This property is unnecessary on this type of catalog, it is always false.",
        replaceWith = ReplaceWith("false"),
    )
    override val isPhysical: Boolean
        get() = false
}

/**
 * A [VersionCatalog] that is defined in a physical file.
 */
interface FileVersionCatalog : VersionCatalog {
    @Deprecated(
        message = "This property is unnecessary on this type of catalog, it is always true.",
        replaceWith = ReplaceWith("true"),
    )
    override val isPhysical: Boolean
        get() = true

    /**
     * The file defining this catalog.
     */
    val location: VirtualFile
}

/**
 * Returns a new catalog containing the entries of this catalog and of the given [other] catalog.
 * Entries from the [other] catalog take precedence over entries from this catalog.
 *
 * If [other] is null, returns this catalog, unchanged.
 */
operator fun VersionCatalog.plus(other: VersionCatalog?): VersionCatalog =
    if (other == null) this else CompositeVersionCatalog(listOf(this, other))

/**
 * A composite [VersionCatalog] that contains multiple catalogs.
 * Entries from the first catalog are superseded by entries from the following catalogs in the list.
 */
class CompositeVersionCatalog(
    @UsedInIdePlugin
    val catalogs: List<VersionCatalog>,
) : VersionCatalog {

    override val entries: Map<String, TraceableString> = buildMap {
        // Last catalogs have the highest priority.
        catalogs.forEach { putAll(it.entries) }
    }

    @Deprecated(
        message = "This doesn't play well with composite catalogs. Check the subtypes of the VersionCatalog instead."
    )
    override val isPhysical: Boolean
        @Suppress("DEPRECATION")
        get() = catalogs.any { it.isPhysical }
}

/**
 * A synthetic version catalog with no entries.
 */
object EmptyVersionCatalog : InMemoryVersionCatalog {
    override val entries: Map<String, Nothing> get() = emptyMap()
}