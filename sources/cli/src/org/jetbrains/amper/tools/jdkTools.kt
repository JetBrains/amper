/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.tools

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands

class JdkToolCommands: CliktCommand(name = "jdk", help = "Run various tools from Amper default JDK") {
    init {
        subcommands(
            JdkToolCommand("jstack"),
            JdkToolCommand("jmap"),
            JdkToolCommand("jps"),
            JdkToolCommand("jcmd"),
        )
    }

    override fun run() = Unit
}
