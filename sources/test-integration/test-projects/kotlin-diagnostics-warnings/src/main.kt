@file:MustUseReturnValues
/*
 * Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

fun foo(): Int = 1 + 2

fun main() {
    foo()

    """
        multiline
    """
}