/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend

/**
 * Simple class to associate enum values by some string key.
 */
abstract class EnumMap<EnumT : Enum<EnumT>, KeyT>(
    valuesGetter: () -> Array<EnumT>,
    private val key: EnumT.() -> KeyT,
) : AbstractMap<KeyT, EnumT>() {
    val enumClass = valuesGetter().first()::class
    override val entries = valuesGetter().associateBy { it.key() }.entries
}