/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.aomBuilder

import org.jetbrains.amper.frontend.api.ValueBase
import org.jetbrains.amper.frontend.schema.Modifiers

fun <T> ValueBase<Map<Modifiers, T>>.simplifyModifiers() =
    value.entries.associate { it.key.map { it.value }.toSet() to it.value }

fun Modifiers.asStringSet() = map { it.value }.toSet()

// Convenient function to extract modifiers.
val ValueBase<out Map<Modifiers, *>>.modifiers get() = value.keys
