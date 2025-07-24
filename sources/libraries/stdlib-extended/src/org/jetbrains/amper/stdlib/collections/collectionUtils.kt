/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.stdlib.collections

/**
 * Applies [block] to each element of the collection.
 * This behaves like [forEach] except that the element is passed as a receiver to [block].
 */
inline fun <T> Collection<T>.withEach(block: T.() -> Unit) = forEach { it.block() }
