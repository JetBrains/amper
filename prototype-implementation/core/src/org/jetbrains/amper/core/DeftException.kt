/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.core

class DeftException : RuntimeException {
    constructor(): super()
    constructor(message: String): super(message)
    constructor(message: String, cause: Throwable): super(message, cause)
}

fun <T> deftFailure(): Result<T> = Result.failure(DeftException())
