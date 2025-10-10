/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The implementation of the C library that a JDK is compatible with.
 */
@Serializable
enum class LibCType(val apiValue: String) {
    @SerialName("glibc")
    GLIBC("glibc"),
    @SerialName("libc")
    LIBC("libc"),
    @SerialName("musl")
    MUSL("musl"),
    @SerialName("c_std_lib")
    C_STD_LIB("c_std_lib"),
}
