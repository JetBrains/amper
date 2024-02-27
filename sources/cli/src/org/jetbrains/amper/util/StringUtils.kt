package org.jetbrains.amper.util

fun String.ensureEndsWith(suffix: String) =
    if (!endsWith(suffix)) (this + suffix) else this
