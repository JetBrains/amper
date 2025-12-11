/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.jdk.provisioning

import java.nio.file.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.walk

/**
 * Finds a valid JDK home directory in this directory.
 *
 * A valid JDK home is a directory that contains the Java compiler at `bin/javac` (or `bin/javac.exe`).
 * It also _usually_ contains a `release` file, but this is not guaranteed (all JDK 9+ should, some JDK 8 don't).
 *
 * JDK archives don't necessarily contain a valid JDK home at their root. They often have additional nested directories,
 * especially on macOS. Here are some examples:
 *
 * ```
 * amazon-corretto-21.jdk
 * ╰─ Contents
 *    ├─ _CodeSignature
 *    ├─ Home
 *    │  ╰─ <jdk here>
 *    ├─ MacOS
 *    ╰─ info.plist
 * ```
 *
 * ```
 * zulu8.90.0.19-ca-jdk8.0.472-macosx_x64
 * ╰─ zulu-8.jdk
 *    ╰─ Contents
 *       ├─ _CodeSignature
 *       ├─ Home
 *       │  ╰─ <jdk here>
 *       ├─ MacOS
 *       ╰─ info.plist
 * ```
 *
 * ```
 * zulu21.46.19-ca-jdk21.0.9-macosx_x64
 * ├─ zulu-21.jdk
 * │  ╰─ Contents
 * │     ├─ _CodeSignature
 * │     ├─ Home
 * │     │  ╰─ <jdk here>
 * │     ├─ MacOS
 * │     ╰─ info.plist
 * ╰─ readme.txt
 * ```
 *
 * ```
 * openlogic-openjdk-8u462-b08-mac-x64
 * ├─ bin
 * │  ├─ javafxpackager
 * │  ╰─ javapackager
 * │     // no other binary - no java, no javac!
 * ├─ jdk1.8.0_462.jdk
 * │  ╰─ Contents
 * │     ├─ Home
 * │     │  ╰─ <jdk here>
 * │     ├─ MacOS
 * │     ╰─ info.plist
 * ├─ jre
 * ├─ lib
 * ╰─ man
 * ```
 */
internal fun Path.findValidJdkHomeDir(): Path {
    walk(PathWalkOption.BREADTH_FIRST, PathWalkOption.INCLUDE_DIRECTORIES)
        .filter { it.isDirectory() }
        .forEach {
            if (it.containsJdkBinaries()) {
                return it
            }
            val contentsHome = it / "Contents/Home"
            if (contentsHome.isDirectory()) {
                return contentsHome
            }
        }
    error("Couldn't find JDK home in $this")
}

private fun Path.containsJdkBinaries(): Boolean = resolve("bin/javac").exists() || resolve("bin/javac.exe").exists()
