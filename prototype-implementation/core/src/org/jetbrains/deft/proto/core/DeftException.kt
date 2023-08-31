package org.jetbrains.deft.proto.core

class DeftException : RuntimeException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}
