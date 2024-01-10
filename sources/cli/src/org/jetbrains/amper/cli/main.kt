/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.cli

import picocli.CommandLine
import kotlin.system.exitProcess

fun main(args: Array<String>): Unit = exitProcess(CommandLine(AmperMain()).execute(*args))
