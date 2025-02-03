/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

class AmperException : RuntimeException()

fun <T> amperFailure(): Result<T> = Result.failure(AmperException())
