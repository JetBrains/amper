/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib

import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.experimental.ExperimentalTypeInference

@OptIn(ExperimentalTypeInference::class)
inline fun <K, V, R> caching(
    cacheProvider: () -> MutableMap<K, V> = { hashMapOf() },
    @BuilderInference block: (MutableMap<K, V>) -> R,
): R {
    contract { callsInPlace(block, InvocationKind.EXACTLY_ONCE) }
    val cache = cacheProvider()
    return block(cache)
}