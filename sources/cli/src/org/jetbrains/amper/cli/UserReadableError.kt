package org.jetbrains.amper.cli

/**
 * Exception that can be directly reported to the user without a stacktrace
 */
class UserReadableError(message: String): RuntimeException(message)

fun userReadableError(message: String): Nothing = throw UserReadableError(message)
