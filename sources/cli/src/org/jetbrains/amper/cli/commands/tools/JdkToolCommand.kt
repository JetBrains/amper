/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands.tools

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.SuspendingNoOpCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.multiple
import com.intellij.util.io.awaitExit
import org.jetbrains.amper.cli.commands.RootCommand
import org.jetbrains.amper.cli.userReadableError
import org.jetbrains.amper.core.system.OsFamily
import org.jetbrains.amper.intellij.CommandLineUtils
import org.jetbrains.amper.jvm.JdkDownloader
import kotlin.io.path.isExecutable
import kotlin.io.path.pathString

class JdkToolCommand: SuspendingNoOpCliktCommand(name = "jdk") {
    init {
        subcommands(
            JdkToolSubcommand("jstack"),
            JdkToolSubcommand("jmap"),
            JdkToolSubcommand("jps"),
            JdkToolSubcommand("jcmd"),
        )
    }

    override fun help(context: Context): String = "Run various tools from Amper default JDK"
}

private class JdkToolSubcommand(private val name: String) : SuspendingCliktCommand(name = name) {

    private val toolArguments by argument(name = "tool arguments").multiple()

    private val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun helpEpilog(context: Context): String = "Use -- to separate $name's arguments from Amper options"

    override suspend fun run() {
        val jdk = JdkDownloader.getJdk(commonOptions.sharedCachesRoot)
        val ext = if (OsFamily.current.isWindows) ".exe" else ""
        val toolPath = jdk.javaExecutable.resolveSibling(name + ext)
        if (!toolPath.isExecutable()) {
            userReadableError("Tool is not present or not executable: $toolPath")
        }

        val cmd = listOf(toolPath.pathString) + toolArguments
        val exitCode = ProcessBuilder(CommandLineUtils.quoteCommandLineForCurrentPlatform(cmd))
            .inheritIO()
            .start()
            .awaitExit()
        if (exitCode != 0) {
            userReadableError("$name exited with exit code $exitCode")
        }
    }
}
