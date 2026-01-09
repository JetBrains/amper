/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

fun String.doCapitalize() = replaceFirstChar { it.titlecase() }

val AmperModule.mavenRepositories: List<RepositoriesModulePart.Repository>
    get() = parts.find<RepositoriesModulePart>()?.mavenRepositories ?: emptyList()

/**
 * Simple class to associate enum values by some string key.
 */
abstract class EnumMap<EnumT : Enum<EnumT>, KeyT>(
    valuesGetter: () -> Array<EnumT>,
    private val key: EnumT.() -> KeyT,
) : AbstractMap<KeyT, EnumT>() {
    override val entries = valuesGetter().associateBy { it.key() }.entries.toMutableSet()
}

/**
 * A class that every enum, participating in
 * schema building, should inherit.
 */
interface SchemaEnum {
    val schemaValue: String
    val outdated: Boolean get() = false
}

interface Context {
    val parent: Context?
    val isLeaf: Boolean
    val pretty: String
    val leaves: Set<Context>
}