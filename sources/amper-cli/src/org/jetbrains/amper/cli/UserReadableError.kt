/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

/**
 * Exception that can be directly reported to the user without a stacktrace
 */
class UserReadableError(override val message: String, val exitCode: Int): RuntimeException(message)

fun userReadableError(message: String, exitCode: Int = 1): Nothing = throw UserReadableError(message, exitCode)
