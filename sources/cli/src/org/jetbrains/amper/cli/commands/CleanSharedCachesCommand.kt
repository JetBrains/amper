/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.requireObject
import org.jetbrains.amper.cli.withBackend
import org.slf4j.LoggerFactory
import kotlin.io.path.deleteRecursively

internal class CleanSharedCachesCommand : SuspendingCliktCommand(name = "clean-shared-caches") {

    val commonOptions by requireObject<RootCommand.CommonOptions>()

    override fun help(context: Context): String = "Remove the Amper caches that are shared between projects"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            val root = backend.context.userCacheRoot
            LoggerFactory.getLogger(javaClass).info("Deleting ${root.path}")
            root.path.deleteRecursively()
        }
    }
}
