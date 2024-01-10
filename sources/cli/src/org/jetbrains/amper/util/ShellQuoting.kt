/*
 * Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package org.jetbrains.amper.util

object ShellQuoting {
    // TODO for quoting windows-style one may use com.jetbrains.toolbox.interop.Win32Integration#convertArgListToString
    //  also see https://learn.microsoft.com/en-us/archive/blogs/twistylittlepassagesallalike/everyone-quotes-command-line-arguments-the-wrong-way

    fun quoteArgumentsPosixShellWay(args: List<String>): String {
        // TODO This is awfully bad, let's backport some code either from ParametersList in IDEA or from .NET
        return args.joinToString(" ") { arg ->
            when {
                arg.contains(" ") -> "'$arg'"
                arg.contains("\'") -> "\"$arg\""
                else -> arg
            }
        }
    }
}