/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.commands

import org.jetbrains.amper.cli.CliContext
import org.jetbrains.amper.cli.project.preparePluginsAndReadModel
import org.jetbrains.amper.frontend.Model

/**
 * An [AmperProjectAwareCommand] that also needs a valid project model.
 */
internal abstract class AmperModelAwareCommand(name: String) : AmperProjectAwareCommand(name) {

    final override suspend fun run(cliContext: CliContext) {
        run(cliContext, cliContext.preparePluginsAndReadModel())
    }

    abstract suspend fun run(cliContext: CliContext, model: Model)
}
