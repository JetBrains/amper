/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

/**
 * Exception that can be directly reported to the user without a stacktrace
 */
class UserReadableError(message: String): RuntimeException(message)

fun userReadableError(message: String): Nothing = throw UserReadableError(message)
