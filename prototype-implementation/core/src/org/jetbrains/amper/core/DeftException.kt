package org.jetbrains.amper.core

class DeftException : RuntimeException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

fun <T> deftFailure(): Result<T> = Result.failure(DeftException())
