/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name

/**
 * Finds a valid JDK home directory in this directory.
 */
internal tailrec fun Path.findHomeDir(): Path {
    if (resolve("bin/javac").exists() || resolve("bin/javac.exe").exists()) {
        return this
    }
    // A lot of archives for macOS contain the JDK under <root>/Contents/Home (e.g. Corretto, Temurin)
    // or <root>/<someDir>/Contents/Home (e.g. Zulu 8/11/17/21, Microsoft 11, ...)
    val contentsHome = resolve("Contents/Home")
    if (contentsHome.isDirectory()) {
        return contentsHome
    }
    // A lot of archives for macOS contain one more single root directory that we need to go through (e.g. Zulu 8,
    // Microsoft 11, OpenLogic 11), sometimes next to some other files like READMEs (e.g. Zulu 11/17/21).
    val directories = listDirectoryEntries().filter { it.isDirectory() }
    if (directories.size == 1) {
        return directories.single().findHomeDir()
    }
    // OpenLogic 8 for mac has a strange layout that looks like a JDK with a `bin`, `jre`, `lib` directory, but actually
    // contains almost nothing in `bin`. Instead, a 'jdk1.8.0_462.jdk' directory is present and contains the real JDK.
    // Maybe other archives are like this too. In any case, we need to handle this.
    val jdkDirs = directories.filter { it.name.startsWith("jdk", ignoreCase = true) }
    if (jdkDirs.size == 1) {
        return jdkDirs.single().findHomeDir()
    }
    error("Couldn't find JDK home in $this")
}
