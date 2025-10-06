/*
 * Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
 */

package test

fun main() {
    val env = System.getenv()

    val lines = buildList {
        add("compose.reload.devToolsEnabled=" + System.getProperty("compose.reload.devToolsEnabled"))
        add("AMPER_SERVER_PORT=" + env["AMPER_SERVER_PORT"])
        add("AMPER_BUILD_TASK=" + env["AMPER_BUILD_TASK"])
        add("AMPER_BUILD_ROOT=" + env["AMPER_BUILD_ROOT"])
    }

    lines.forEach { println(it) }
}
