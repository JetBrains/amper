/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.plugins.schema.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Computed default values for schema properties/parameters.
 */
@Serializable
sealed interface Defaults {
    @Serializable
    @SerialName("boolean")
    data class BooleanDefault(
        val value: Boolean,
    ) : Defaults

    @Serializable
    @SerialName("int")
    data class IntDefault(
        val value: Int,
    ) : Defaults

    @Serializable
    @SerialName("string")
    data class StringDefault(
        val value: String,
    ) : Defaults

    // TODO: Path?

    @Serializable
    @SerialName("enum")
    data class EnumDefault(
        val value: String,
    ) : Defaults

    @Serializable
    @SerialName("list")
    data class ListDefault(
        val value: List<Defaults>,
    ) : Defaults

    @Serializable
    @SerialName("map")
    data class MapDefault(
        val value: Map<String, Defaults>,
    ) : Defaults

    @Serializable
    @SerialName("null")
    data object Null : Defaults
}