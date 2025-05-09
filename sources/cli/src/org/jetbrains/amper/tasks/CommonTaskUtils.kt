/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tasks

import org.jetbrains.amper.frontend.Fragment

object CommonTaskUtils {
    fun Iterable<Fragment>.userReadableList() = map { it.name }.sorted().joinToString(" ")
}

/**
 * A phrase identifying these fragments:
 * ```
 * fragments [x, y, z] of module 'my-module'
 * ```
 */
fun Collection<Fragment>.identificationPhrase(): String = when (size) {
    0 -> "no fragments"
    1 -> single().identificationPhrase()
    else -> "fragments ${map { it.name }.sorted()} of module '${first().module.userReadableName}'"
}

/**
 * A phrase identifying this fragment:
 * ```
 * fragment 'x' of module 'my-module'
 * ```
 */
fun Fragment.identificationPhrase(): String = "fragment '${name}' of module '${module.userReadableName}'"
