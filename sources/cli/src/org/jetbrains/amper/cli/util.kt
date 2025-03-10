/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import org.jetbrains.amper.core.AmperUserCacheInitializationFailure
import org.jetbrains.amper.core.AmperUserCacheInitializationResult
import org.jetbrains.amper.core.AmperUserCacheRoot

fun AmperUserCacheInitializationResult.unwrap(): AmperUserCacheRoot = when (this) {
    is AmperUserCacheInitializationFailure -> userReadableError(defaultMessage)
    is AmperUserCacheRoot -> this
}
