/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.foojay.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

/**
 * The system architecture that a JDK is compatible with.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
enum class Architecture(val apiValue: String) {
    @SerialName("aarch64")
    @JsonNames("arm64")
    AARCH64("aarch64"),
    @SerialName("aarch32")
    @JsonNames("arm", "arm32")
    AARCH32("aarch32"),
    @SerialName("armel")
    ARMEL("armel"),
    @SerialName("armhf")
    ARMHF("armhf"),
    @SerialName("ia64")
    IA64("ia64"),
    @SerialName("mips")
    MIPS("mips"),
    @SerialName("mipsel")
    MIPSEL("mipsel"),
    @SerialName("ppc")
    PPC("ppc"),
    @SerialName("ppc64")
    PPC64("ppc64"),
    @SerialName("ppc64le")
    PPC64LE("ppc64le"),
    @SerialName("riscv64")
    RISCV64("riscv64"),
    @SerialName("s390x")
    S390X("s390x"),
    @SerialName("sparc")
    SPARC("sparc"),
    @SerialName("sparcv9")
    SPARCV9("sparcv9"),
    @SerialName("x86")
    @JsonNames("x32", "i386", "i586", "i686")
    X86("x86"),
    @SerialName("x86_64")
    @JsonNames("x64", "amd64")
    X86_64("x86_64");

    companion object {

        /**
         * The [Architecture] value representing the current system.
         */
        fun current(): Architecture = when (val arch = System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64", "armv8" -> AARCH64
            "amd64", "x64", "x86-64", "x86_64", "x86lx64" -> X86_64
            "aarch32", "arm32", "armv6", "armv7l", "armv7", "arm" -> AARCH32
            "armel" -> ARMEL
            "armhf" -> ARMHF
            "ia64", "ia-64" -> IA64
            "mips" -> MIPS
            "mipsel" -> MIPSEL
            "ppc" -> PPC
            "ppc64" -> PPC64
            "ppc64el", "ppc64le" -> PPC64LE
            "riscv64", "risc-v", "riscv" -> RISCV64
            "s390", "s390x" -> S390X
            "sparc" -> SPARC
            "sparcv9" -> SPARCV9
            "x32", "x86", "x86-32", "x86lx32", "286", "386", "486", "586", "686", "i386", "i486", "i586", "i686" -> X86
            else -> error("Unknown architecture: $arch")
        }
    }
}
