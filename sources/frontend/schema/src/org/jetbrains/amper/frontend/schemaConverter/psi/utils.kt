/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.frontend.schemaConverter.psi

/**
 * Returns a copy of this map, without the entries that have null values.
 */
@Suppress("UNCHECKED_CAST")
internal fun <K, V : Any> Map<K, V?>.filterNotNullValues(): Map<K, V> = filterValues { it != null } as Map<K, V>
