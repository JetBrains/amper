/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli.options

import com.github.ajalt.clikt.core.ParameterHolder
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.enum
import org.jetbrains.amper.util.BuildType

internal fun ParameterHolder.buildTypeOption(
    help: String,
) = option(
    "-v",
    "--variant",
    help = help,
).enum<BuildType> { it.value }