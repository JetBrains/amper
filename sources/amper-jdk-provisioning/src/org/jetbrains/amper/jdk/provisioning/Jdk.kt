/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import org.jetbrains.amper.frontend.schema.JvmDistribution
import java.nio.file.Path
import kotlin.io.path.exists

class Jdk(
    /**
     * The root directory of this JDK installation (which could be used as JAVA_HOME).
     */
    val homeDir: Path,
    /**
     * The full version number, including major/minor/path, but also potential extra numbers.
     */
    val version: String,
    /**
     * The Java distribution of this JDK (Temurin, Corretto, Zulu, etc.).
     *
     * May be 'null' if we reused a JAVA_HOME JDK and couldn't infer its distribution.
     */
    val distribution: JvmDistribution?,
    /**
     * Opaque information about where this JDK comes from (usually "JAVA_HOME" or a download URL).
     * It is not meant to be used for control flow but rather for telemetry or statistics.
     */
    val source: String,
) {
    // not lazy so we immediately validate that the java executable is present
    val javaExecutable: Path = bin("java")
    val javacExecutable: Path by lazy { bin("javac") }

    private fun bin(executableName: String): Path {
        val plain = homeDir.resolve("bin/$executableName")
        if (plain.exists()) return plain

        val exe = homeDir.resolve("bin/$executableName.exe")
        if (exe.exists()) return exe

        error("No $executableName executables were found under $homeDir")
    }
}
