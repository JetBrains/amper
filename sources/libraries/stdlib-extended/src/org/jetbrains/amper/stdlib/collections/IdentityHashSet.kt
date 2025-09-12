/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.collections

import java.util.*

/**
 * Creates a set based on the [IdentityHashMap].
 */
@Suppress("FunctionName")
fun <T> IdentityHashSet(): MutableSet<T> = Collections.newSetFromMap(IdentityHashMap<T, Boolean>())

/**
 * A set based on the [IdentityHashMap] with the [initial] elements.
 */
@Suppress("FunctionName")
fun <T> IdentityHashSet(initial: Collection<T>): MutableSet<T> =
    Collections.newSetFromMap(IdentityHashMap<T, Boolean>()).apply { addAll(initial) }