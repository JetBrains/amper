/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.intellij

import com.intellij.execution.CommandLineUtil
import com.intellij.execution.Platform

object CommandLineUtils {
    fun quoteCommandLineForCurrentPlatform(cmd: List<String>): List<String> =
        CommandLineUtil.toCommandLine(cmd[0], cmd.subList(1, cmd.size), SystemInfoRt.currentPlatform())
}

// Copy-paste from IntelliJ [SystemInfoRt] due to a small binary incompatibility.
object SystemInfoRt {
    val OS_NAME: String
    val OS_VERSION: String

    init {
        var name = System.getProperty("os.name")
        var version = System.getProperty("os.version").lowercase()

        if (name.startsWith("Windows") && name.matches("Windows \\d+".toRegex())) {
            // for whatever reason, JRE reports "Windows 11" as a name and "10.0" as a version on Windows 11
            try {
                val version2 = name.substring("Windows".length + 1) + ".0"
                if (version2.toFloat() > version.toFloat()) {
                    version = version2
                }
            } catch (ignored: NumberFormatException) {
            }
            name = "Windows"
        }

        OS_NAME = name
        OS_VERSION = version
    }

    private val _OS_NAME = OS_NAME.lowercase()
    val isWindows: Boolean = _OS_NAME.startsWith("windows")
    fun currentPlatform()= if (isWindows) Platform.WINDOWS else Platform.UNIX
}

