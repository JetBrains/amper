/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import org.jetbrains.amper.cli.commands.AmperSubcommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.intellij.CommandLineUtils
import org.jetbrains.amper.jvm.JdkDownloader
import org.jetbrains.amper.processes.runProcessWithInheritedIO
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

internal class JdkToolCommand: AmperSubcommand(name = "jdk") {
    init {
        subcommands(
            JdkToolSubcommand("jstack"),
            JdkToolSubcommand("jmap"),
            JdkToolSubcommand("jps"),
            JdkToolSubcommand("jcmd"),
        )
    }

    override fun help(context: Context): String = "Run various tools from Amper default JDK"

    override suspend fun run() = Unit
}

private class JdkToolSubcommand(private val name: String) : AmperSubcommand(name = name) {

    private val toolArguments by argument(name = "tool_arguments").multiple()

    override fun helpEpilog(context: Context): String = "Use -- to separate $name's arguments from Amper options"

    override suspend fun run() {
        val jdk = JdkDownloader.getJdk(commonOptions.sharedCachesRoot)
        val ext = if (OsFamily.current.isWindows) ".exe" else ""
        val toolPath = jdk.javaExecutable.resolveSibling(name + ext)
        if (!toolPath.isExecutable()) {
            userReadableError("Tool is not present or not executable: $toolPath")
        }

        val cmd = listOf(toolPath.pathString) + toolArguments
        val exitCode = runProcessWithInheritedIO(command = CommandLineUtils.quoteCommandLineForCurrentPlatform(cmd))
        if (exitCode != 0) {
            userReadableError("$name exited with exit code $exitCode")
        }
    }
}
