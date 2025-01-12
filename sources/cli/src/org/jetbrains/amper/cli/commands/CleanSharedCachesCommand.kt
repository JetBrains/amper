/*
 * Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import com.github.ajalt.clikt.core.Context
import org.jetbrains.amper.cli.withBackend
import org.slf4j.LoggerFactory
import kotlin.io.path.deleteRecursively

internal class CleanSharedCachesCommand : AmperSubcommand(name = "clean-shared-caches") {

    override fun help(context: Context): String = "Remove the Amper caches that are shared between projects"

    override suspend fun run() {
        withBackend(commonOptions, commandName) { backend ->
            val root = backend.context.userCacheRoot
            val logger = LoggerFactory.getLogger(javaClass)
            logger.info("Deleting shared caches at ${root.path}...")
            root.path.deleteRecursively()
            logger.info("Deletion complete.")
        }
    }
}
