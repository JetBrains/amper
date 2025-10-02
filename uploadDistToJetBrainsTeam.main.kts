/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.pathString

val amperRootDir: Path = __FILE__.toPath().absolute().parent // __FILE__ is this script

@Suppress("PROCESS_BUILDER_START_LEAK")
fun runAmperCli(vararg args: String): Int {
    val isWindows = System.getProperty("os.name").startsWith("Win", ignoreCase = true)
    val amperScript = amperRootDir.resolve(if (isWindows) "amper.bat" else "amper")
    return ProcessBuilder(amperScript.pathString, *args).inheritIO().start().waitFor()
}

val result = runAmperCli("task", ":amper-cli:uploadDistToJetBrainsTeam")

check(result == 0) {
    "Uploading task failed: $result. Check the output above"
}