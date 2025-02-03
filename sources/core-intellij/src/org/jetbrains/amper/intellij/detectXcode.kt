/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intellij

fun detectXcodeInstallation(): String {
    val process = ProcessBuilder()
        .command("xcode-select", "-print-path")
        .start()

    val resultCode = process.waitFor()
    val xcodeSelectOut = if (resultCode != 0) null else process
        .inputStream
        .buffered()
        .reader()
        .buffered()
        .readLine()

    return xcodeSelectOut ?: error(
        "Failed to detect Xcode. Make sure Xcode is installed.\n" +
                "Consider specifying it manually via the `xcode.base` property."
    )
}