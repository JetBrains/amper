/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.types

interface SchemaEnumDeclaration : SchemaTypeDeclaration {
    /**
     * Enum [entries][EnumEntry]
     */
    val entries: List<EnumEntry>

    /**
     * Whether the declaration order of the [entries] is somehow important, and it should be honored while presenting
     * them to the user (e.g., autocomplete).
     */
    val isOrderSensitive: Boolean

    /**
     * Returns the enum constant with the [enum name][Enum.name] matching the given [name].
     * `null` if this enum is not builtin (e.g., coming from a plugin).
     *
     * Throws [NoSuchElementException] if this is a builtin enum but there is no such constant.
     */
    fun toEnumConstant(name: String): Enum<*>?

    /**
     * Returns the enum entry with the [schema value][EnumEntry.schemaValue] matching the given [schemaValue].
     * `null` if there is no such entry.
     */
    fun getEntryBySchemaValue(schemaValue: String): EnumEntry?

    /**
     * Returns the enum entry with the [name][EnumEntry.name] matching the given [name].
     * `null` if there is no such entry.
     */
    fun getEntryByName(name: String): EnumEntry?

    override fun toType(): SchemaType.EnumType = SchemaType.EnumType(this)

    data class EnumEntry(
        /**
         * Enum entry's name, as in [Enum.name], both for builtin enums and user-defined enums from plugins.
         */
        val name: String,

        /**
         * Enum entry's text representation used in user-facing YAML configurations.
         */
        val schemaValue: String,
        val isOutdated: Boolean = false,
        val isIncludedIntoJsonSchema: Boolean = true,
        val documentation: String? = null,
        val origin: SchemaOrigin,
    )
}