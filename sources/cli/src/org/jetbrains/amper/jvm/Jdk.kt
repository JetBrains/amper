/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jvm

import java.net.URL
import java.nio.file.Path
import kotlin.io.path.exists

class Jdk(
    /**
     * The root directory of this JDK installation (which could be used as JAVA_HOME).
     */
    val homeDir: Path,
    /**
     * The URL from which this JDK was downloaded.
     */
    val downloadUrl: URL,
    /**
     * The full version number, including major/minor/path, but also potential extra numbers.
     */
    val version: String,
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
